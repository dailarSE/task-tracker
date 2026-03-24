package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TemplateTypeProcessorTest {

    private final TemplateTypeProcessor processor = new TemplateTypeProcessor();

    @ParameterizedTest
    @EnumSource(TemplateType.class)
    @DisplayName("Should successfully resolve all known Template Types")
    void shouldResolveKnownTemplates(TemplateType type) {
        PipelineItem item = createItemWithTemplateHeaderField(type.name());

        processor.process(item);

        assertTrue(item.isPending(), "Item should remain PENDING");
        assertEquals(type, item.getTemplateType(), "Resolved TemplateType mismatch");
    }

    @Test
    @DisplayName("Should be case-insensitive when resolving template IDs")
    void shouldBeCaseInsensitive() {
        PipelineItem item = createItemWithTemplateHeaderField("user_welcome");

        processor.process(item);

        assertTrue(item.isPending());
        assertEquals(TemplateType.USER_WELCOME, item.getTemplateType());
    }

    @Test
    @DisplayName("Should reject with MALFORMED_TRANSPORT when header is missing")
    void shouldRejectMissingHeader() {
        PipelineItem item = createItemWithTemplateHeaderField(null);

        processor.process(item);

        assertEquals(PipelineItem.Status.FAILED, item.getStatus());
        assertEquals(RejectReason.MALFORMED_TRANSPORT, item.getRejectReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN_TEMPLATE", " ", "INVALID"})
    @DisplayName("Should reject with DATA_INCONSISTENCY for unknown or malformed template IDs")
    void shouldRejectUnknownTemplates(String unknownType) {
        PipelineItem item = createItemWithTemplateHeaderField(unknownType);

        processor.process(item);

        assertEquals(PipelineItem.Status.FAILED, item.getStatus());
        assertEquals(RejectReason.DATA_INCONSISTENCY, item.getRejectReason());
        assertNotNull(item.getRejectCause(), "Should preserve the cause");
    }

    private PipelineItem createItemWithTemplateHeaderField(String headerValue) {
        var record = TestKafkaConsumerRecordFactory.record().build();
        PipelineItem item = new PipelineItem(record);
        item.setTemplateIdHeader(headerValue);
        return item;
    }
}