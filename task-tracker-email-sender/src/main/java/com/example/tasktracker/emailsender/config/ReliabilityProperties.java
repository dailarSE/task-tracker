package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.config.init.InconsistentConfigurationException;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;


@ConfigurationProperties(prefix = "app.email.reliability")
@Validated
@RequiredArgsConstructor
@Getter
@Setter
public class ReliabilityProperties {
    private static final double BATCH_TIMEOUT_SAFETY_MARGIN = 0.9;
    private static final Duration MINIMAL_FINALIZATION_BUFFER = Duration.ofSeconds(10);

    @Valid
    private DispatchCapacity capacity = new DispatchCapacity();
    @Valid
    private NetworkLimit networkLimit = new NetworkLimit();
    @Valid
    private Budget budget = new Budget();
    @Valid
    private Idempotency idempotency = new Idempotency();
    @Valid
    private CircuitBreakerPolicy circuitBreakerPolicy = new CircuitBreakerPolicy();

    /**
     * Параметры пропускной способности (Throughput).
     */
    @Getter
    @Setter
    public static class DispatchCapacity {

        /**
         * Монотонно возрастающая версия конфигурации пропускной способности.
         */
        @Positive
        private long configurationVersion = 1;

        /**
         * Глобальный лимит запросов в секунду (RPS) на весь кластер.
         * Используется для ограничения нагрузки на внешний SMTP-провайдер.
         */
        @Positive
        private int clusterWideRateLimit = 200;

        /**
         * Максимальное количество одновременно открытых сетевых соединений (Active Connections).
         * Защищает системные ресурсы (сокеты, память) и предотвращает блокировки провайдером за шторм запросов.
         */
        @Positive
        private int maxActiveConnections = 50;

        /**
         * Размер порции данных для одного шага обработки. Определяет "гранулярность" потребления глобального лимита.
         */
        @Positive
        private int tokenChunkSize = 20;

        /**
         * Максимальное время ожидания разрешений (токенов) из распределенной квоты для следующего чанка.
         */
        private Duration acquisitionTimeout = Duration.ofSeconds(3);
    }

    /**
     * Иерархия сетевых таймаутов SMTP.
     */
    @Getter
    @Setter
    public static class NetworkLimit {
        /**
         * Порог фиксации "медленного вызова". Позволяет зафиксировать деградацию провайдера до обрыва связей.
         */
        @NotNull
        private Duration slowCallThreshold = Duration.ofMillis(1500);

        /**
         * Таймаут установки соединения.
         */
        @NotNull
        private Duration connectTimeout = Duration.ofMillis(2000);

        /**
         * Жесткая отсечка выполнения задачи на уровне приложения.
         * Гарантирует возврат управления, если SMTP-библиотека зависла.
         */
        @NotNull
        private Duration executionDeadline = Duration.ofMillis(4000);

        /**
         * Таймаут чтения из сокета (на уровне ОС).
         */
        @NotNull
        private Duration socketReadTimeout = Duration.ofMillis(5000);
    }

    /**
     * Контракт Batch Processing.
     */
    @Getter
    @Setter
    public static class Budget {
        /**
         * Размер батча, запрашиваемого из Kafka (eq. max.poll.records).
         */
        @Positive
        private int commandIntakeBatchSize = 500;

        /**
         * Максимально допустимое время на полную обработку одного батча.
         * Должно быть меньше, чем {@code max.poll.interval.ms} в настройках Kafka.
         */
        @NotNull
        private Duration maxBatchProcessingTime = Duration.ofSeconds(90);
    }

    /**
     * Настройки дедупликации и жизненного цикла блокировок.
     */
    @Getter
    @Setter
    public static class Idempotency {
        /**
         * TTL для короткой блокировки на время обработки сообщения (статус PROCESSING).
         */
        @NotNull
        private Duration processingLockDuration = Duration.ofMinutes(5);

        /**
         * Карта настроек TTL для хранения статуса успешной обработки (SENT) или фатальной ошибки (FAILED)
         * для каждого типа шаблона.
         */
        private Map<TemplateType, Duration> retentionPolicies = new EnumMap<>(TemplateType.class);

        /**
         * Возвращает TTL для конкретного типа шаблона или значение по умолчанию
         */
        public Duration getTtlFor(TemplateType type) {
            return retentionPolicies.getOrDefault(type, Duration.ofDays(7));
        }
    }

    @Getter
    @Setter
    public static class CircuitBreakerPolicy {

        @DecimalMin("0")
        @DecimalMax("100.0")
        @NotNull
        private BigDecimal failureRateThreshold = new BigDecimal("50.0");
        @DecimalMin("0")
        @DecimalMax("100.0")
        @NotNull
        private BigDecimal slowCallRateThreshold = new BigDecimal("50.0");
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        @Positive
        private int permittedNumberOfCallsInHalfOpenState = 20;
    }

    /**
     * Выполняет проверку критических инвариантов системы при старте.
     *
     * @throws InconsistentConfigurationException если обнаружено нарушение логики таймаутов или пропускной способности.
     */
    @PostConstruct
    public void validate() {
        assertNetworkCascade();
        assertBatchThroughput();
        assertIdempotencyLeaseSafety();
    }

    private void assertNetworkCascade() {
        long slow = networkLimit.slowCallThreshold.toMillis();
        long conn = networkLimit.connectTimeout.toMillis();
        long limit = networkLimit.executionDeadline.toMillis();
        long read = networkLimit.socketReadTimeout.toMillis();

        if (slow >= conn || conn >= limit || limit >= read) {
            throw new InconsistentConfigurationException(
                    String.format("TIMING VIOLATION: Network cascade must be: SlowCall(%d) < Connect(%d) < TimeLimiter(%d) < SocketRead(%d)",
                            slow, conn, limit, read),
                    "Adjust app.email.reliability.network-limit settings to preserve the required order."
            );
        }
    }

    private void assertBatchThroughput() {
        long estimatedProcessingTime = calculateWorstCaseProcessingTime();
        long allowedBudget = (long) (budget.maxBatchProcessingTime.toMillis() * BATCH_TIMEOUT_SAFETY_MARGIN);

        if (estimatedProcessingTime > allowedBudget) {
            throw new InconsistentConfigurationException(
                    String.format("THROUGHPUT VIOLATION: Batch processing risk. Estimated: %dms, Budget: %dms",
                            estimatedProcessingTime, allowedBudget),
                    "Increase max-batch-processing-time, decrease command-intake-batch-size, or increase cluster-wide-rate-limit."
            );
        }
    }

    private void assertIdempotencyLeaseSafety() {
        long minSafeLease = budget.maxBatchProcessingTime.toMillis() + MINIMAL_FINALIZATION_BUFFER.toMillis();
        long actualLease = idempotency.processingLockDuration.toMillis();

        if (actualLease < minSafeLease) {
            throw new InconsistentConfigurationException(
                    String.format("IDEMPOTENCY VIOLATION: Processing lock (%dms) must be > BatchTimeout + Buffer (%dms)",
                            actualLease, minSafeLease),
                    "Increase app.email.reliability.idempotency.processing-lock-duration to avoid premature lock expirations."
            );
        }
    }

    private long calculateWorstCaseProcessingTime() {
        double networkSteps = Math.ceil((double) budget.commandIntakeBatchSize / capacity.maxActiveConnections);
        long networkTime = (long) (networkSteps * networkLimit.executionDeadline.toMillis());

        long rpsWaitTime = (long) (((double) budget.commandIntakeBatchSize / capacity.clusterWideRateLimit) * 1000);

        return networkTime + rpsWaitTime;
    }
}
