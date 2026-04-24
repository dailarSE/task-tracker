package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.RateLimitContext;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;

public class ChunkRateLimitConvention extends BaseO11yConvention<RateLimitContext> {
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
