package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class NoOpSender implements Sender {
    @Override
    public CompletableFuture<Void> sendAsync(PipelineItem item) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> sendChunkAsync(List<PipelineItem> chunk) {
        return CompletableFuture.completedFuture(null);
    }
}
