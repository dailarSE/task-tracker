package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TtlFormatProcessorTest {

    private static final String ISO_DATE_HEADER = "2025-01-01T12:00:00Z";
    private static final long RECORD_TIMESTAMP = 1735725600000L; // 2025-01-01T10:00:00Z
    private static final Duration CUSTOM_TTL = Duration.ofHours(2);

    private TtlFormatProcessor processor;
    private EmailSenderProperties props;

    @BeforeEach
    void setUp() {
        props = new EmailSenderProperties();
        props.getMessageValidity().getPolicies().put(TemplateType.USER_WELCOME, CUSTOM_TTL);
        props.getMessageValidity().setDefaultDuration(Duration.ofDays(1));

        processor = new TtlFormatProcessor(props);
    }

    @Test
    @DisplayName("Priority: Header should take precedence over calculated TTL")
    void shouldPrioritizeHeaderDeadline() {
        var item = createItem(RECORD_TIMESTAMP, TemplateType.USER_WELCOME);
        item.setValidUntilHeader(ISO_DATE_HEADER);

        processor.process(item);

        assertEquals(Instant.parse(ISO_DATE_HEADER), item.getDeadline(),
                "Deadline must match the header value, ignoring record timestamp");
        assertTrue(item.getStage().isPending());
    }

    @Test
    @DisplayName("Fallback: Should calculate deadline from Record Timestamp when header is missing")
    void shouldCalculateDeadlineFromPolicy() {
        var item = createItem(RECORD_TIMESTAMP, TemplateType.USER_WELCOME);
        item.setValidUntilHeader(null);

        processor.process(item);

        Instant expected = Instant.ofEpochMilli(RECORD_TIMESTAMP).plus(CUSTOM_TTL);
        assertEquals(expected, item.getDeadline(), "Deadline should be Record TS + Policy Duration");
    }

    @Test
    @DisplayName("Default Policy: Should use default duration if no specific policy exists for TemplateType")
    void shouldUseCaseDefaultDuration() {
        var item = createItem(RECORD_TIMESTAMP, TemplateType.DAILY_TASK_REPORT);

        processor.process(item);

        Instant expected = Instant.ofEpochMilli(RECORD_TIMESTAMP).plus(props.getMessageValidity().getDefaultDuration());
        assertEquals(expected, item.getDeadline());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-01-01", "tomorrow", "invalid-date"})
    @DisplayName("Error Handling: Should reject with DATA_INCONSISTENCY if header format is invalid")
    void shouldRejectInvalidHeaderFormat(String invalidDate) {
        var item = createItem(RECORD_TIMESTAMP, TemplateType.USER_WELCOME);
        item.setValidUntilHeader(invalidDate);

        processor.process(item);

        assertEquals(PipelineItem.Status.FAILED, item.getStage().status());
        assertEquals(RejectReason.DATA_INCONSISTENCY, item.getStage().rejectReason());
        assertInstanceOf(java.time.format.DateTimeParseException.class, item.getStage().rejectCause());
    }

    private PipelineItem createItem(long timestamp, TemplateType type) {
        var record = TestKafkaConsumerRecordFactory.record()
                .timestamp(timestamp)
                .build();
        PipelineItem item = new PipelineItem(record);
        item.setTemplateType(type);
        return item;
    }
}