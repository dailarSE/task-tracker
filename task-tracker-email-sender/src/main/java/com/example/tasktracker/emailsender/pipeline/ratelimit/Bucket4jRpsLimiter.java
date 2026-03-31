package com.example.tasktracker.emailsender.pipeline.ratelimit;

import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import com.example.tasktracker.emailsender.exception.infrastructure.StateStoreInfrastructureException;
import io.github.bucket4j.BlockingBucket;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class Bucket4jRpsLimiter implements RpsLimiter{

    private final BlockingBucket rpsBucket;
    private final int maxChunkSize;
    private final Duration acquisitionTimeout;

    public Bucket4jRpsLimiter(BlockingBucket rpsBucket, ReliabilityProperties properties) {
        this.rpsBucket = rpsBucket;

        this.maxChunkSize = properties.getCapacity().getTokenChunkSize();
        this.acquisitionTimeout = properties.getCapacity().getAcquisitionTimeout();
    }

    /**
     * Запрашивает разрешение на обработку чанка сообщений.
     * <p>
     * Метод блокируется до тех пор, пока не будет получено достаточное количество токенов,
     * или пока не истечет таймаут {@code acquisitionTimeout}. Размер запрашиваемой
     * пачки токенов ограничен внутренним {@code maxChunkSize} для обеспечения
     * плавности (smoothness) и справедливости (fairness) распределения квоты
     * между нодами кластера.
     *
     * @param requestedAmount Желаемое количество сообщений для обработки.
     * @return Фактическое количество разрешений (токенов), полученных для обработки (размер чанка).
     * @throws RateLimitExceededException если квота не была получена в течение таймаута.
     * @throws StateStoreInfrastructureException при сбоях инфраструктуры (например, Redis недоступен).
     * @throws InterruptedException если поток был прерван во время ожидания.
     */
    public int acquire(int requestedAmount) throws InterruptedException{
        int toTake = Math.min(requestedAmount, maxChunkSize);

        try {
            log.trace("Attempting to acquire {} tokens...", toTake);
            boolean acquired = rpsBucket.tryConsume(toTake, acquisitionTimeout); // blocking

            if (!acquired) {
                log.error("Rate Limit acquisition timed out. Requested: {}", toTake);
                throw new RateLimitExceededException(
                        "Rate Limit too strict. Required wait time exceeds limit of " + acquisitionTimeout
                );
            }

            log.trace("Acquired {} tokens", toTake);
            return toTake;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Rate Limiter infrastructure failure", e);
            throw new StateStoreInfrastructureException("Rate Limiter infrastructure failure", e);
        }
    }
}