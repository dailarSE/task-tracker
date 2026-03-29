package com.example.tasktracker.emailsender.exception.infrastructure;

public class InfrastructureSuspendedException extends InfrastructureException {
    public InfrastructureSuspendedException(String message, Throwable cause) {
        super(message, cause);
    }
}
