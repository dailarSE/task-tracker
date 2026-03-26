package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Sender {
    CompletableFuture<Void> sendAsync(PipelineItem item);
    CompletableFuture<Void> sendChunkAsync(List<PipelineItem> chunk);

}
