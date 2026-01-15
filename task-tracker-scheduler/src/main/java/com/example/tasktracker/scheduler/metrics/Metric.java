package com.example.tasktracker.scheduler.metrics;

import lombok.Getter;

/**
 * Централизованный реестр всех метрик в приложении.
 */
@Getter
public enum Metric {
    // --- Метрики состояния джобы (Job State) ---
    JOB_STATE_DESERIALIZATION_ERROR(
            "tasktracker.scheduler.job_state.deserialization.errors",
            "Counts errors during JobState JSON deserialization," +
                    " indicating potentially corrupt data in the persistence layer."
    ),
    JOB_STATE_SERIALIZATION_ERROR(
            "tasktracker.scheduler.job_state.serialization.errors",
            "Counts critical errors during JobState JSON serialization."
    ),
    JOB_STATE_MIGRATION_SUCCESS(
            "tasktracker.scheduler.job_state.migration.success",
            "Counts successful on-the-fly migrations of old JobState schema versions."
    ),

    // --- Метрики для Job ---
    JOB_RUN_FAILURE("tasktracker.scheduler.job.runs.failed",
            "Total number of failed job runs."),
    JOB_EVENTS_PUBLISHED("tasktracker.scheduler.job.events.published.total",
            "Total number of events successfully published to Kafka by a job."),
    JOB_KAFKA_SEND_FAILURE("tasktracker.scheduler.job.kafka.send.failed",
            "Counts failures to send an event to Kafka within a job."),
    JOB_RUN_DURATION("tasktracker.scheduler.job.run.duration",
                             "Measures the total execution time of a scheduled job run."),

    // --- Метрики для Batch Consumer ---
    JOB_BATCH_SIZE("tasktracker.scheduler.job.batch.size",
                           "Histogram of batch sizes consumed from Kafka."),
    JOB_ITEM_SKIPPED("tasktracker.scheduler.job.item.skipped",
                             "Count of items skipped during processing (e.g. no data from backend).");

    private final String name;
    private final String description;

    Metric(String name, String description) {
        this.name = name;
        this.description = description;
    }
}