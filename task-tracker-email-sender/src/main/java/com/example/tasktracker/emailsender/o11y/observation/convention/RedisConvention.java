package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.RedisObservationContext;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import jakarta.validation.constraints.NotNull;

import static com.example.tasktracker.emailsender.o11y.observation.convention.RedisObservationTags.HighCardinality.*;
import static com.example.tasktracker.emailsender.o11y.observation.convention.RedisObservationTags.LowCardinality.*;


public class RedisConvention extends BaseO11yConvention<RedisObservationContext> {

    @Override
    public @NotNull KeyValues getLowCardinalityKeyValues(RedisObservationContext context) {
        String procedureName = context.getScriptSha1() == null ? context.getLogicalOperationName() : context.getScriptSha1();

        return super.getLowCardinalityKeyValues(context).and(
                SYSTEM.asString(), "redis",
                OPERATION.asString(), context.getOperationName(),
                LOGICAL_OPERATION.asString(), context.getLogicalOperationName(),
                NAMESPACE.asString(), context.getDatabaseIndex(),
                SERVER_ADDRESS.asString(), context.getServerAddress(),
                SERVER_PORT.asString(), String.valueOf(context.getPort()),
                PROCEDURE_NAME.asString(), procedureName
        );
    }

    @Override
    public @NotNull KeyValues getHighCardinalityKeyValues(@NotNull RedisObservationContext context) {
        KeyValues kvs = super.getHighCardinalityKeyValues(context);

        if (context.getBatchSize() != null && context.getBatchSize() > 1) {
            kvs = kvs.and(BATCH_SIZE.asString(), String.valueOf(context.getBatchSize()));
        }

        return kvs;
    }

    @Override
    public String getName() {
        return "db.client.operation.duration";
    }

    @Override
    public String getContextualName(RedisObservationContext context) {
        // [SemConv] Span name SHOULD be {db.operation.name} {db.stored_procedure.name}
        String spanName = context.getScriptSha1() == null ? context.getLogicalOperationName() : context.getScriptSha1();
        return context.getOperationName() + " " + spanName;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context.getClass() == RedisObservationContext.class;
    }
}