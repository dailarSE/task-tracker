package com.example.tasktracker.emailsender.pipeline.model;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Getter
@Slf4j
public class PipelineItem {

    public enum Status {
        PENDING, SKIPPED, RETRY, FAILED, SENT
    }

    public record ExecutionStage(
            @NonNull Status status,
            @NonNull RejectReason rejectReason,
            @Nullable String rejectDescription,
            @Nullable Exception rejectCause
    ) {
        public ExecutionStage {
            Objects.requireNonNull(status, "Status cannot be null");
            Objects.requireNonNull(rejectReason, "RejectReason cannot be null");
        }

        public boolean isFailed() {
            return status == Status.FAILED || status == Status.RETRY;
        }

        public boolean isPending() {
            return status == Status.PENDING;
        }

        public Optional<RuntimeException> toException() {
            return switch (status) {
                case RETRY ->
                        Optional.of(new RetryableProcessingException(rejectReason, rejectDescription, rejectCause));
                case FAILED -> Optional.of(new FatalProcessingException(rejectReason, rejectDescription, rejectCause));
                default -> Optional.empty();
            };
        }
    }

    private static final ExecutionStage INITIAL_STAGE =
            new ExecutionStage(Status.PENDING, RejectReason.NONE, null, null);
    private static final ExecutionStage STAGE_SENT =
            new ExecutionStage(Status.SENT, RejectReason.NONE, null, null);

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

    private TriggerCommand payload;
    private final ConsumerRecord<byte[], byte[]> originalRecord;

    private final AtomicReference<ExecutionStage> stage = new AtomicReference<>(INITIAL_STAGE);

    public PipelineItem(@NonNull ConsumerRecord<byte[], byte[]> originalRecord) {
        this.originalRecord = originalRecord;
    }

    public boolean tryMarkAsSent() {
        return tryTerminate(STAGE_SENT);
    }

    public boolean tryReject(@NonNull Status status, @NonNull RejectReason reason, @NonNull String description) {
        return tryReject(status, reason, description, null);
    }

    public boolean tryReject(@NonNull Status status, @NonNull RejectReason reason, @NonNull String description, Exception cause) {
        validateRejectionStatus(status);
        return tryTerminate(new ExecutionStage(status, reason, description, cause));
    }

    private boolean tryTerminate(ExecutionStage nextState) {
        boolean won = stage.compareAndSet(INITIAL_STAGE, nextState);

        if (!won) {
            log.warn("Thread {} lost state race for item {}. Current state: {}, Attempted: {}",
                    Thread.currentThread().getName(),
                    getCoordinates(),
                    stage.get().status(),
                    nextState.status());
        }
        return won;
    }

    public void setPayload(TriggerCommand payload) {
        if (this.payload != null) {
            throw new IllegalStateException("Payload already set");
        }
        this.payload = payload;
    }

    public ExecutionStage getStage() {
        return stage.get();
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
}