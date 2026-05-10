package com.example.tasktracker.emailsender.pipeline.idempotency;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class IdempotencyStatus {
    public static final String SENT = "SENT";
    public static final String PROCESSING = "PROCESSING";
}
