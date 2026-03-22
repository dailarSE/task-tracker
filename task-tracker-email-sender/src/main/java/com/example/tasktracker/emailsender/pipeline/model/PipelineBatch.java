package com.example.tasktracker.emailsender.pipeline.model;

import java.util.List;
import java.util.function.Consumer;

public record PipelineBatch (List<PipelineItem> items) {
    public List<PipelineItem> getPendingItems() {
        return items.stream().filter(PipelineItem::isPending).toList();
    }

    public List<PipelineItem> getSentItems() {
        return items.stream()
                .filter(item -> item.getStatus() == PipelineItem.Status.SENT)
                .toList();
    }

    public void forEachPending(Consumer<PipelineItem> action) {
        for (int i = 0; i < items.size(); i++) {
            PipelineItem item = items.get(i);
            if (item.isPending()) {
                action.accept(item);
            }
        }
    }


}