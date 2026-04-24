package com.example.tasktracker.emailsender.o11y.pipeline;

import com.example.tasktracker.emailsender.o11y.observation.context.RateLimitContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.ChunkRateLimitConvention;
import com.example.tasktracker.emailsender.pipeline.ratelimit.Bucket4jRpsLimiter;
import com.example.tasktracker.emailsender.pipeline.ratelimit.RpsLimiter;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ObservedBucket4jRpsLimiter implements RpsLimiter {

    private final Bucket4jRpsLimiter delegate;
    private final ObservationRegistry registry;
    private final ChunkRateLimitConvention rateLimitConvention;

    @SuppressWarnings("DataFlowIssue")
    public int acquire(int requestedAmount) throws InterruptedException {
        RateLimitContext context = new RateLimitContext("global-rps");

        return Observation
                .createNotStarted(rateLimitConvention, () -> context, registry)
                .observeChecked(() -> delegate.acquire(requestedAmount));
    }
}
