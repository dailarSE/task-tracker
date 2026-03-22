package com.example.tasktracker.emailsender.exception.infrastructure;

import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;

public class InfrastructureException extends RetryableProcessingException {
    public InfrastructureException(RejectReason rejectReason, String message) {
        super(rejectReason, message);
    }

    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }

    public InfrastructureException(String message) {
        super(message);
    }
}
