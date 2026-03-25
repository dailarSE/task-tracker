package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;

public interface IdempotencyCommitter {
    void commitSuccess(PipelineItem item);
    void commitSuccess(PipelineBatch batch);
}
