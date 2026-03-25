package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;

public interface IdempotencyGuard {
    void checkAndLock(PipelineBatch batch) throws InfrastructureException;
    void checkAndLock(PipelineItem item) throws InfrastructureException;
}
