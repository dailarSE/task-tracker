package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import com.example.tasktracker.emailsender.o11y.observation.context.RateLimitContext;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import lombok.Getter;

public class ChunkRateLimitConvention extends BaseO11yConvention<RateLimitContext> {
    @Getter
    private final double[] sloBuckets;

    public enum Names {
        METRIC_NAME("email_sender.rate_limit.wait.duration"),
        SPAN_NAME("rate_limit wait");

        private final String value;

        Names(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum LowCardinalityTags implements KeyName {
        LIMITER_ID {
            @Override
            public String asString() {
                return "rl.limiter.id";
            }
        }
    }

    public ChunkRateLimitConvention(ReliabilityProperties properties) {
        long max = properties.getCapacity().getAcquisitionTimeout().toNanos();
        sloBuckets = new double[]{max * 0.01, max * 0.05, max * 0.1, max * 0.25, max * 0.5, max * 0.75, max};
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(RateLimitContext context) {
        return super.getLowCardinalityKeyValues(context).and(
                LowCardinalityTags.LIMITER_ID.asString(), context.getLimiterId());
    }

    @Override
    public String getContextualName(RateLimitContext context) {
        return Names.SPAN_NAME.value;
    }

    @Override
    public String getName() {
        return Names.METRIC_NAME.value;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof RateLimitContext;
    }
}
