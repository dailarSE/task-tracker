package com.example.tasktracker.emailsender.pipeline.ratelimit;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.pipeline.ChunkingExecutor;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.pipeline.sender.Sender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public class RpsLimitedChunkingExecutor implements ChunkingExecutor {
    private final RpsLimiter rpsLimiter;
    private final Sender asyncSender;
    private final EmailSenderProperties.RateLimitProperties properties;
    private final Clock clock;

    @Override
    public void execute(List<PipelineItem> items) throws InterruptedException, InfrastructureException {
        if (items.isEmpty()) return;

        final Instant deadline = Instant.now(clock).plus(properties.getBatchProcessingTimeout());
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

        } catch (InfrastructureException e) {
            abortAll(sendingFutures, "Retryable failure: " + e.getClass().getSimpleName());
            throw e;
        } catch (TimeoutException e) {
            abortAll(sendingFutures, "Deadline reached during batch execution");
            throw new InfrastructureSuspendedException("Batch execution timed out", e);
        } catch (InterruptedException e) {
            abortAll(sendingFutures, "Execution interrupted");
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            abortAll(sendingFutures, "Unhandled failure: " + e.getMessage());
            throw new FatalProcessingException(
                    RejectReason.INTERNAL_ERROR, "Chunk orchestration bug: " + e.getMessage(), e);
        }
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
