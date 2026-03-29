package com.example.tasktracker.emailsender.pipeline.ratelimit;

import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;

public class RateLimitExceededException extends InfrastructureException {
    public RateLimitExceededException(String message) {
        super(message, null);
    }
}
