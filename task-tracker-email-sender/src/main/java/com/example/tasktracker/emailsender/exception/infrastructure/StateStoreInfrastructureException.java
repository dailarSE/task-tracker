package com.example.tasktracker.emailsender.exception.infrastructure;

import com.example.tasktracker.emailsender.pipeline.model.RejectReason;

public class StateStoreInfrastructureException extends InfrastructureException {
    public StateStoreInfrastructureException(String message) {
        super(message);
    }

    public StateStoreInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }

    public StateStoreInfrastructureException(RejectReason rejectReason, String message) {
        super(rejectReason, message);
    }
}
