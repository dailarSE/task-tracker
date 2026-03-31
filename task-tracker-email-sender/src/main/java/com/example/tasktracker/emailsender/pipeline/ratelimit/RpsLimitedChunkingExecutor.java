package com.example.tasktracker.emailsender.pipeline.ratelimit;

import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.pipeline.ChunkingExecutor;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.pipeline.sender.Sender;
import com.example.tasktracker.emailsender.util.AsyncUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
public class RpsLimitedChunkingExecutor implements ChunkingExecutor {
    private final RpsLimiter rpsLimiter;
    private final Sender asyncSender;
    private final ReliabilityProperties properties;
    private final Clock clock;

    @Override
    public void execute(List<PipelineItem> items) {
        if (items.isEmpty()) return;

        final Instant deadline = Instant.now(clock).plus(properties.getBudget().getMaxBatchProcessingTime());
        List<CompletableFuture<Void>> sendingFutures = new ArrayList<>();
        int cursor = 0;

        try {
            while (cursor < items.size()) {
                ensureNotExpired(deadline);

                int remaining = items.size() - cursor;

                int chunkSize = rpsLimiter.acquire(remaining);

                List<PipelineItem> chunk = items.subList(cursor, cursor + chunkSize);

                sendingFutures.add(asyncSender.sendChunkAsync(chunk));

                cursor += chunkSize;
            }

            waitForCompletion(sendingFutures, deadline);

        } catch (Throwable t) {
            abortAll(sendingFutures, "Batch error: " + t.getClass().getSimpleName());
            handleBatchError(t);
        }
    }

    private void handleBatchError(Throwable t) {
        Throwable cause = AsyncUtils.unwrap(t);

        throw switch (cause) {
            case RetryableProcessingException e -> e;
            case FatalProcessingException e -> e;
            case TimeoutException e -> new InfrastructureSuspendedException("Batch deadline exceeded", e);
            case InterruptedException e -> {
                Thread.currentThread().interrupt();
                yield new InfrastructureSuspendedException("Batch execution interrupted", e);
            }
            case CancellationException e -> new InfrastructureSuspendedException("Batch was cancelled", e);
            default -> new FatalProcessingException(RejectReason.INTERNAL_ERROR, "Chunking orchestration bug", cause);
        };
    }

    private void ensureNotExpired(Instant deadline) throws TimeoutException {
        if (Instant.now(clock).isAfter(deadline)) {
            throw new TimeoutException("Batch processing deadline exceeded");
        }
    }

    private void waitForCompletion(List<CompletableFuture<Void>> futures, Instant deadline)
            throws InterruptedException, ExecutionException, TimeoutException {

        Duration remaining = Duration.between(Instant.now(clock), deadline);

        if (remaining.isNegative() || remaining.isZero()) {
            throw new TimeoutException("No time left for async completion");
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(remaining.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void abortAll(List<CompletableFuture<Void>> futures, String reason) {
        if (futures.isEmpty()) return;

        log.warn("Aborting {} active sending tasks. Reason: {}", futures.size(), reason);
        futures.forEach(f -> f.cancel(true));
    }
}
