package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetadataResolverTest {

    private static final String EXPECTED_TEMPLATE = "USER_WELCOME";
    private static final String EXPECTED_VALID_UNTIL = "2025-01-01T12:00:00Z";
    private static final String EXPECTED_CORRELATION_ID = "test-correlation-id-123";

    private MetadataResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MetadataResolver();
    }

    @Test
    @DisplayName("Should extract all mandatory and optional headers correctly")
    void shouldExtractHeaders() {
        var record = createRecordWithHeaders();
        PipelineItem item = new PipelineItem(record);

        resolver.process(item);

        assertEquals(EXPECTED_TEMPLATE, item.getTemplateIdHeader(), "Template ID header mismatch");
        assertEquals(EXPECTED_VALID_UNTIL, item.getValidUntilHeader(), "Valid Until header mismatch");
        assertEquals(EXPECTED_CORRELATION_ID, item.getCorrelationIdHeader(), "Correlation ID header mismatch");
    }

    @Test
    @DisplayName("Should set nulls when headers are missing to avoid stale data")
    void shouldHandleMissingHeaders() {
        var record = TestKafkaConsumerRecordFactory.record().build();
        PipelineItem item = new PipelineItem(record);

        resolver.process(item);

        assertNull(item.getTemplateIdHeader());
        assertNull(item.getValidUntilHeader());
        assertNull(item.getCorrelationIdHeader());
    }

    private ConsumerRecord<byte[], byte[]> createRecordWithHeaders() {
        return TestKafkaConsumerRecordFactory.record()
                .template(EXPECTED_TEMPLATE)
                .validUntil(EXPECTED_VALID_UNTIL)
                .correlationId(EXPECTED_CORRELATION_ID)
                .build();
    }
}