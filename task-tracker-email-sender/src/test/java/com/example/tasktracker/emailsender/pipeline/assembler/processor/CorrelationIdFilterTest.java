package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("Should pass when Correlation ID is present and valid")
    void shouldPassValidCorrelationId() {
        PipelineItem item = createItemWithCorrelationId("corr-123");

        filter.process(item);

        assertTrue(item.getStage().isPending(), "Item should remain PENDING when CID is valid");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("Should reject when Correlation ID is null, empty or blank")
    void shouldRejectInvalidCorrelationId(String invalidCid) {
        PipelineItem item = createItemWithCorrelationId(invalidCid);

        filter.process(item);

        assertEquals(PipelineItem.Status.FAILED, item.getStage().status(), "Item status should be FAILED");
        assertEquals(RejectReason.MALFORMED_TRANSPORT, item.getStage().rejectReason(), "Reject reason mismatch");
        assertTrue(item.getStage().rejectDescription().contains("X-Correlation-ID"), "Error message should mention the header");
    }

    private PipelineItem createItemWithCorrelationId(String cid) {
        var record = TestKafkaConsumerRecordFactory.record().correlationId(cid).build();
        var item = new PipelineItem(record);
        item.setCorrelationIdHeader(cid);
        return item;
    }
}