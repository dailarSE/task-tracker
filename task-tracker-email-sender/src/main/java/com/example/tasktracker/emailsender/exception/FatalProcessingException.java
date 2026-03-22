package com.example.tasktracker.emailsender.exception;

import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.Getter;

/**
 * Это исключение должно классифицироваться как "NonRetryable" в конфигурации Kafka Consumer.
 */

public class FatalProcessingException extends RuntimeException {
    @Getter
    private RejectReason rejectReason;

    public FatalProcessingException(RejectReason rejectReason, String message) {
        super(message);
        this.rejectReason = rejectReason;
    }

    public FatalProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FatalProcessingException(String message) {
        super(message);
    }
}
