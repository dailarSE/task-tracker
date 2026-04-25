package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.api.email.EmailHeaders;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.example.tasktracker.emailsender.config.ResilienceConfig.*;
import static com.example.tasktracker.emailsender.util.AsyncUtils.unwrap;

@Component
@Slf4j
public class ResilientSmtpEmailClient implements EmailClient {

    private final EmailTransport transport;
    private final EmailTemplateEngine templateEngine;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService vThreadExecutor;


    public ResilientSmtpEmailClient(EmailTransport transport,
                                    EmailTemplateEngine templateEngine,
                                    @Qualifier(EMAIL_CIRCUIT_BREAKER) CircuitBreaker circuitBreaker,
                                    @Qualifier(EMAIL_BULKHEAD) Bulkhead bulkhead,
                                    @Qualifier(EMAIL_TIME_LIMITER) TimeLimiter timeLimiter,
                                    @Qualifier("resilienceScheduler") ScheduledExecutorService scheduledExecutor,
                                    @Qualifier("virtualThreadExecutor") ExecutorService vThreadExecutor) {
        this.transport = transport;
        this.templateEngine = templateEngine;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.timeLimiter = timeLimiter;
        this.scheduledExecutor = scheduledExecutor;
        this.vThreadExecutor = vThreadExecutor;
    }

    /**
     * Асинхронно выполняет подготовку (рендеринг) и отправку письма в изолированном контексте
     * с применением политик надежности (TimeLimiter, CircuitBreaker, Bulkhead).
     * <p>
     * supplyAsync для того, чтобы Bulkhead.acquirePermission() выполнился внутри Виртуального Потока,
     * а не заблокировал вызывающий поток.
     *
     * @param sendCommand DTO с входными данными для формирования письма.
     * @return {@link CompletableFuture}, который успешно завершается при доставке письма провайдеру,
     * или завершается со следующими типами ошибок:
     * <ul>
     *   <li>{@link RetryableProcessingException} — если сработала защита (Circuit Breaker OPEN,
     *       Bulkhead FULL, Timeout), или транспорт сообщил о временной недоступности.</li>
     *   <li>{@link FatalProcessingException} — если данные письма или шаблона некорректны,
     *   или произошел критический программный сбой.</li>
     *   <li>{@link InfrastructureSuspendedException} — если выполнение было принудительно прервано.</li>
     * </ul>
     */

    public CompletableFuture<Void> send(TriggerCommand sendCommand) {
        return CompletableFuture
                .supplyAsync(() -> decorateWithResilience(sendCommand), vThreadExecutor)
                .thenCompose(Function.identity())
                .handle(this::translateException);
    }

    private CompletionStage<Void> decorateWithResilience(TriggerCommand sendCommand) {
        Supplier<CompletionStage<Void>> task = () -> startAsyncExecution(sendCommand);

        return Decorators.ofCompletionStage(task)
                .withTimeLimiter(timeLimiter, scheduledExecutor)
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .get();
    }

    /**
     * Реализует мост между синхронным транспортом и асинхронным контрактом.
     * <p>
     * Данный уровень вложенности является вынужденным решением для обеспечения корректного жизненного цикла задачи
     * внутри "луковицы" декораторов. Это позволяет избежать конфликтов между моделью исполнения JVM и логикой
     * стороннего лимитера, чья реализация накладывает нетривиальные ограничения на оркестрацию потоков.
     */
    private CompletableFuture<Void> startAsyncExecution(TriggerCommand sendCommand) {
        return CompletableFuture.runAsync(() -> {
                    var renderingResult = templateEngine.process(
                            sendCommand.templateId(),
                            sendCommand.templateContext(),
                            sendCommand.localeTag()
                    );

                    HashMap<String, String> headers = new HashMap<>();
                    headers.put(EmailHeaders.X_CORRELATION_ID, sendCommand.correlationId());
                    headers.put(EmailHeaders.X_TEMPLATE_ID, sendCommand.templateId());

                    transport.send(new SendInstructions(
                            sendCommand.recipientEmail(),
                            renderingResult.subject(),
                            renderingResult.body(),
                            true,
                            headers
                    ));

                },
                vThreadExecutor);
    }

    /**
     * Стерилизует цепочку исключений, превращая асинхронный контекст в доменные типы.
     */
    Void translateException(Void result, Throwable ex) {
        if (ex == null) return result;

        if (Thread.currentThread().isInterrupted()) {
            throw new InfrastructureSuspendedException("Execution was interrupted", unwrap(ex));
        }

        Throwable core = unwrap(ex);

        throw switch (core) {
            case RetryableProcessingException e -> e;
            case FatalProcessingException e -> e;

            case InterruptedException e -> {
                Thread.currentThread().interrupt();
                yield new InfrastructureSuspendedException("Execution interrupted during send", e);
            }
            case CancellationException e -> new InfrastructureSuspendedException("Task was cancelled", e);

            case Exception e when
                    e instanceof TimeoutException || e.getClass().getPackageName().startsWith("io.github.resilience4j") ->
                    new RetryableProcessingException(RejectReason.INFRASTRUCTURE, "Resilience safety trigger", e);

            case RuntimeException e ->
                    new FatalProcessingException(RejectReason.INTERNAL_ERROR, "Unexpected processing fault", e);

            default -> new FatalProcessingException(RejectReason.INTERNAL_ERROR, "Critical system failure", core);
        };
    }
}