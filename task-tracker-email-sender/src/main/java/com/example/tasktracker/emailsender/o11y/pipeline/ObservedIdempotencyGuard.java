package com.example.tasktracker.emailsender.o11y.pipeline;

import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.o11y.observation.context.RedisContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.RedisObservationContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.RedisConvention;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyGuard;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@RequiredArgsConstructor
public class ObservedIdempotencyGuard implements IdempotencyGuard {
    private final IdempotencyGuard delegate;
    private final RedisContextFactory contextFactory;
    private final ObservationRegistry registry;
    private final String scriptSha1;
    private final RedisConvention redisConvention;

    private static final String OPERATION_NAME = "EVAL";
    private static final String LOGICAL_OPERATION_NAME = "idempotency.check";

    public ObservedIdempotencyGuard(IdempotencyGuard delegate,
                                    RedisContextFactory contextFactory,
                                    ObservationRegistry registry,
                                    RedisScript<List<String>> delegateIdempotencyScript,
                                    RedisConvention redisConvention) {
        this.delegate = delegate;
        this.contextFactory = contextFactory;
        this.registry = registry;
        this.scriptSha1 = delegateIdempotencyScript.getSha1();
        this.redisConvention = redisConvention;
    }

    @Override
    public void checkAndLock(PipelineBatch batch) throws InfrastructureException {
        if (batch.items().isEmpty()) return;

        RedisObservationContext context = contextFactory.createContext(
                OPERATION_NAME,
                LOGICAL_OPERATION_NAME,
                batch.items().size(),
                scriptSha1
        );

        Observation.createNotStarted(redisConvention, () -> context, registry)
                .observeChecked(() -> delegate.checkAndLock(batch));
    }

    @Override
    public void checkAndLock(PipelineItem item) {
        RedisObservationContext context = contextFactory.createContext(
                OPERATION_NAME,
                LOGICAL_OPERATION_NAME,
                null,
                scriptSha1
        );

        Observation.createNotStarted(redisConvention, () -> context, registry)
                .observeChecked(() -> delegate.checkAndLock(item));
    }
}
