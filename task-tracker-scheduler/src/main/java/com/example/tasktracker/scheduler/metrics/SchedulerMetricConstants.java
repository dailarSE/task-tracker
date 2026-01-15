package com.example.tasktracker.scheduler.metrics;

import io.micrometer.core.instrument.Tag;

public final class SchedulerMetricConstants {
    private SchedulerMetricConstants() {}

    public static final String TAG_TYPE = "type";
    public static final String TAG_VALUE_EMAIL_COMMAND = "email_command";

    public static final Tag TAG_EMAIL_COMMAND = Tag.of(TAG_TYPE, TAG_VALUE_EMAIL_COMMAND);
}