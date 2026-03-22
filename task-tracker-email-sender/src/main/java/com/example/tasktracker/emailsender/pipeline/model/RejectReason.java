package com.example.tasktracker.emailsender.pipeline.model;

public enum RejectReason {
    NONE,
    MALFORMED_TRANSPORT,
    MALFORMED_JSON,
    TTL_EXPIRED,
    INVALID_PAYLOAD,
    DATA_INCONSISTENCY,
    KEY_GENERATION,
    DUPLICATE,
    CONCURRENT_LOCK,
    INFRASTRUCTURE, REMOTE_ERROR, INTERNAL_ERROR
}
