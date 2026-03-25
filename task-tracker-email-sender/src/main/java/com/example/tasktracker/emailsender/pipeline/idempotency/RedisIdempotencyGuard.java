package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.infrastructure.StateStoreInfrastructureException;
import com.example.tasktracker.emailsender.infra.RuntimeInstanceIdProvider;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RedisIdempotencyGuard implements IdempotencyGuard {
    private static final String STATUS_ACQUIRED = "ACQUIRED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_PROCESSING = "PROCESSING";

    private final StringRedisTemplate redisTemplate;
    private final TemplateKeyRegistry keyRegistry;
    private final RuntimeInstanceIdProvider idProvider;
    private final RedisScript<List<String>> idempotencyScript;

    private final Duration lockTtl;

    public RedisIdempotencyGuard(
            EmailSenderProperties properties,
            StringRedisTemplate redisTemplate,
            TemplateKeyRegistry keyRegistry,
            RuntimeInstanceIdProvider idProvider,
            RedisScript<List<String>> idempotencyScript) {
        lockTtl = properties.getIdempotency().getProcessingLockDuration();
        this.redisTemplate = redisTemplate;
        this.keyRegistry = keyRegistry;
        this.idProvider = idProvider;
        this.idempotencyScript = idempotencyScript;
    }

    /**
     * Выполняет проверку на дубликаты и захватывает временные блокировки для батча.
     */
    public void checkAndLock(PipelineBatch batch) {
        guardAll(batch.getPendingItems());
    }

    /**
     * Выполняет проверку на дубликаты и захватывает временную блокировку для одиночного элемента.
     */
    public void checkAndLock(PipelineItem item) {
        if (item.isPending()) {
            guardAll(List.of(item));
        }
    }

    /**
     * Атомарно проверяет дедупликацию и захватывает локи для списка элементов.
     * Использует Fencing (координаты сообщения + ID инстанса) для идентификации владельца.
     * <p>
     * Логика состояний:
     * 1. ACQUIRED: Чистый захват или самозахват текущим воркером — разрешает отправку.
     * 2. SENT: Сообщение уже имеет статус завершения — помечает элемент как SKIPPED.
     * 3. PROCESSING: Ключ занят другим воркером или иным офсетом — помечает элемент для ретрая.
     * </p>
     *
     * @param pendingItems Список элементов для проверки и блокировки.
     * @throws StateStoreInfrastructureException если хранилище состояний недоступно или вернуло некорректный ответ.
     */
    private void guardAll(List<PipelineItem> pendingItems) {
        if (pendingItems.isEmpty()) return;

        String instanceId = idProvider.getInstanceId();
        List<String> keys = new ArrayList<>(pendingItems.size());
        List<String> leaseValues = new ArrayList<>(pendingItems.size());
        List<PipelineItem> itemsInScope = new ArrayList<>(pendingItems.size());

        for (PipelineItem item : pendingItems) {
            try {
                keys.add(buildDeduplicationKey(item));
                leaseValues.add(item.getCoordinates() + "|" + instanceId);
                itemsInScope.add(item);
            } catch (Exception e) {
                item.reject(
                        PipelineItem.Status.FAILED,
                        RejectReason.KEY_GENERATION,
                        "Failed to generate dedup key: " + e.getMessage(),
                        e
                );
                log.debug("Idempotency KeyGen failed at [{}]. Marking FAILED.", item.getCoordinates(), e);
            }
        }

        if (keys.isEmpty()) return;

        List<String> lockStatuses = acquireLeases(keys, leaseValues);

        for (int i = 0; i < itemsInScope.size(); i++) {
            updateItemStateBasedOnLock(itemsInScope.get(i), keys.get(i), lockStatuses.get(i));
        }
    }

    private String buildDeduplicationKey(PipelineItem item) {
        TriggerCommand cmd = item.getPayload();
        return keyRegistry
                .forType(item.getTemplateType())
                .build(cmd);
    }

    /**
     * ARGV структура: [lease1, lease2, ..., leaseN, lockTtl, SENT_STATUS]
     *
     * @throws StateStoreInfrastructureException если хранилище состояний недоступно или вернуло некорректный ответ.
     */
    private List<String> acquireLeases(List<String> keys, List<String> leaseValues) {
        try {
            List<String> args = new ArrayList<>(leaseValues);
            args.add(String.valueOf(lockTtl.toSeconds()));
            args.add(STATUS_SENT);

            List<String> results = redisTemplate.execute(
                    idempotencyScript,
                    keys,
                    args.toArray()
            );

            if (results == null) {
                String errorMsg = "Redis returned NULL for idempotency script execution.";
                log.debug(errorMsg);
                throw new StateStoreInfrastructureException(errorMsg);
            }

            if (results.size() != keys.size()) {
                String errorMsg = String.format("Redis returned unexpected number of results. Expected: '%d', Got: '%d'",
                        keys.size(), results.size());
                log.debug(errorMsg);
                throw new StateStoreInfrastructureException(errorMsg);
            }
            return results;
        } catch (Exception e) {
            log.debug("Idempotency store communication failure.", e);
            if (e instanceof StateStoreInfrastructureException)
                throw e;
            throw new StateStoreInfrastructureException("Idempotency guard is unavailable", e);
        }
    }

    private void updateItemStateBasedOnLock(PipelineItem item, String key, String status) {
        switch (status) {
            case STATUS_ACQUIRED -> log.trace("Lock acquired for key: '{}'", key);
            case STATUS_SENT -> {
                item.reject(
                        PipelineItem.Status.SKIPPED,
                        RejectReason.DUPLICATE,
                        "Message already processed (key: " + key + ")"
                );
                log.trace("Deduplication: key '{}' already processed (SENT).", key);
            }
            case STATUS_PROCESSING -> {
                item.reject(
                        PipelineItem.Status.RETRY,
                        RejectReason.CONCURRENT_LOCK,
                        "Resource is temporarily locked by another worker"
                );
                log.debug("Lease conflict: key '{}' is busy (PROCESSING). Marking for retry.", key);
            }
            case null, default -> {
                String errorMsg = String.format("Protocol error: unexpected status '%s' for key '%s' " +
                        "Expected one of: [ACQUIRED, SENT, PROCESSING]", status, key);
                log.error(errorMsg);
                throw new StateStoreInfrastructureException(errorMsg);
            }
        }
    }
}
