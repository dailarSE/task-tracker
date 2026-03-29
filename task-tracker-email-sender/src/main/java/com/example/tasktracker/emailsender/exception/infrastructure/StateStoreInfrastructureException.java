package com.example.tasktracker.emailsender.exception.infrastructure;

public class StateStoreInfrastructureException extends InfrastructureException {

    public StateStoreInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }

    public StateStoreInfrastructureException(String message) {
        this(message, null);
    }
}
