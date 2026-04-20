package com.example.tasktracker.emailsender.o11y.pipeline;

import com.example.tasktracker.emailsender.o11y.observation.context.RedisContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.RedisObservationContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.RedisConvention;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyCommitter;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ObservedIdempotencyCommitter implements IdempotencyCommitter {
    private static final String CONV_OPERATION_NAME = "PIPELINE";
    private static final String DISCRETE_CONV_OPERATION_NAME = "SET";

    private static final String LOGICAL_OPERATION_NAME = "idempotency.commit";

    private final IdempotencyCommitter delegate;
    private final RedisContextFactory contextFactory;
    private final ObservationRegistry registry;
    private final RedisConvention redisConvention;

    @Override
    public void commitSuccess(PipelineBatch batch) {
        var sentItems = batch.getSentItems();
        if (sentItems.isEmpty()) return;

        RedisObservationContext context = contextFactory.createContext(
                CONV_OPERATION_NAME,
                LOGICAL_OPERATION_NAME,
                sentItems.size(),
                null
        );

        Observation.createNotStarted(redisConvention, () -> context, registry)
                .observe(() -> delegate.commitSuccess(batch));
    }

    @Override
    public void commitSuccess(PipelineItem item) {
        RedisObservationContext context = contextFactory.createContext(
                DISCRETE_CONV_OPERATION_NAME,
                LOGICAL_OPERATION_NAME,
                null,
                null);

        Observation.createNotStarted(redisConvention, () -> context, registry)
                .observe(() -> delegate.commitSuccess(item));
    }
}