package com.example.tasktracker.emailsender.exception;

import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.Getter;

/**
 * Это исключение должно классифицироваться как "Retryable" в конфигурации Kafka Consumer.
 */
public class RetryableProcessingException extends RuntimeException {
    @Getter
    private RejectReason rejectReason;

    public RetryableProcessingException(RejectReason rejectReason, String message) {
        super(message);
        this.rejectReason = rejectReason;
    }

    public RetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableProcessingException(String message) {
        super(message);
    }
}
