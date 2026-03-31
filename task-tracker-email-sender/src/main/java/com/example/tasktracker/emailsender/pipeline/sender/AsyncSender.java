package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.example.tasktracker.emailsender.util.AsyncUtils.unwrap;

@Slf4j
@RequiredArgsConstructor
public class AsyncSender implements Sender {
    private final EmailClient emailClient;

    @Override
    public CompletableFuture<Void> sendAsync(PipelineItem item) {
        return emailClient.send(item.getPayload())
                .handle((result, throwable) -> {
                    if (throwable == null)
                        item.markAsSent();
                    else
                        handleError(item, throwable);
                    return result;
                });
    }

    @Override
    public CompletableFuture<Void> sendChunkAsync(List<PipelineItem> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = chunk.stream()
                .map(this::sendAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void handleError(PipelineItem item, Throwable t) {
        Throwable cause = unwrap(t);

        switch (cause) {
            case RetryableProcessingException e ->
                    item.reject(PipelineItem.Status.RETRY, e.getRejectReason(), e.getMessage(), e);
            case CancellationException e ->
                    item.reject(PipelineItem.Status.RETRY, RejectReason.INFRASTRUCTURE, "Batch processing cancelled", e);
            case FatalProcessingException e ->
                    item.reject(PipelineItem.Status.FAILED, e.getRejectReason(), e.getMessage(), e);
            default -> {
                item.reject(PipelineItem.Status.FAILED, RejectReason.INTERNAL_ERROR, "Unexpected error", cause);
                log.error("Unhandled error during async send for item [{}].", item.getCoordinates(), cause);
            }
        }
    }
}