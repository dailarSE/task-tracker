package com.example.tasktracker.emailsender.pipeline.dispatch;

import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;

public class BrokerUnavailableException extends InfrastructureException {
    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
