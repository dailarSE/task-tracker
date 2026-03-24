package com.example.tasktracker.emailsender.pipeline.model;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Instant;

@Getter
public class PipelineItem {
    @Setter
    private String templateIdHeader;
    @Setter
    private String validUntilHeader;
    @Setter
    private String correlationIdHeader;

    @Setter
    private TemplateType templateType;
    @Setter
    private Instant deadline;

    @Setter
    private TriggerCommand payload;
    private final ConsumerRecord<byte[], byte[]> originalRecord;

    private volatile Status status = Status.PENDING;
    private RejectReason rejectReason = RejectReason.NONE;
    private String rejectDescription;
    private Throwable rejectCause;

    public PipelineItem(@NonNull ConsumerRecord<byte[], byte[]> originalRecord) {
        this.originalRecord = originalRecord;
    }

    public enum Status {
        PENDING, // Готов к обработке
        SKIPPED, // Пропущен
        RETRY,   // Временная ошибка отправки (в ретрай)
        FAILED,  // Фатальная ошибка (в DLT)
        SENT     // Успешно отправлен
    }

    public void markAsSent() {
        assertIsPending();
        this.status = Status.SENT;
    }

    public void reject(@NonNull Status status, @NonNull RejectReason reason, @NonNull String description) {
        validateRejectionStatus(status);
        validateDescription(description);
        assertIsPending();

        this.status = status;
        this.rejectReason = reason;
        this.rejectDescription = description;
    }

    public void reject(@NonNull Status status, @NonNull RejectReason reason, @NonNull String description, Throwable cause) {
        this.reject(status, reason, description);
        this.rejectCause = cause;
    }


    public boolean isPending() {
        return this.status == Status.PENDING;
    }

    public boolean isFailed() {
        return status == Status.FAILED || status == Status.RETRY;
    }

    public String getCoordinates() {
        return originalRecord.topic() + "-" + originalRecord.partition() + "@" + originalRecord.offset();
    }

    private void validateRejectionStatus(Status status) {
        if (status == Status.PENDING || status == Status.SENT) {
            throw new IllegalArgumentException(
                    "Invalid rejection status: " + status + ". Expected SKIPPED, RETRY, or FAILED."
            );
        }
    }

    private void validateDescription(String description) {
        if (description.isBlank()) {
            throw new IllegalArgumentException("Rejection description cannot be empty or blank.");
        }
    }

    private void assertIsPending() {
        if (!isPending()) {
            throw new IllegalStateException(
                    "Cannot modify a terminal item. Current status: " + this.status
            );
        }
    }
}