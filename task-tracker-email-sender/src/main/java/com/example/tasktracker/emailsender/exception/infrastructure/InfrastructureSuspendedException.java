package com.example.tasktracker.emailsender.exception.infrastructure;

import com.example.tasktracker.emailsender.pipeline.model.RejectReason;

public class InfrastructureSuspendedException extends InfrastructureException {
    public InfrastructureSuspendedException(RejectReason rejectReason, String message) {
        super(rejectReason, message);
    }

    public InfrastructureSuspendedException(String message, Throwable cause) {
        super(message, cause);
    }

    public InfrastructureSuspendedException(String message) {
        super(message);
    }
}
