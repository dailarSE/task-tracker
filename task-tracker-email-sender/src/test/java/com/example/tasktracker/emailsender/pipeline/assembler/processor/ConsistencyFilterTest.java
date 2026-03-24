package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistencyFilterTest {

    private final ConsistencyFilter filter = new ConsistencyFilter();

    @Test
    @DisplayName("Happy Path: Should pass when both IDs match in header and body")
    void shouldPassWhenConsistent() {
        var item = createItem("WELCOME", "corr-1", "WELCOME", "corr-1");

        filter.process(item);

        assertTrue(item.isPending(), "Item should stay PENDING");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInconsistencyScenarios")
    @DisplayName("Should reject with DATA_INCONSISTENCY when IDs do not match")
    void shouldRejectInconsistentData(String description, String hTid, String hCid, String bTid, String bCid, String expectedMessagePart) {
        // Given
        var item = createItem(hTid, hCid, bTid, bCid);

        // When
        filter.process(item);

        // Then
        assertEquals(PipelineItem.Status.FAILED, item.getStatus());
        assertEquals(RejectReason.DATA_INCONSISTENCY, item.getRejectReason());
        assertTrue(item.getRejectDescription().contains(expectedMessagePart),
                "Error message should mention the mismatching field");
    }

    private static Stream<Arguments> provideInconsistencyScenarios() {
        return Stream.of(
                Arguments.of(
                        "Template ID mismatch",
                        "WELCOME_EMAIL", "c-1",
                        "DAILY_REPORT", "c-1",
                        "Template ID mismatch"
                ),
                Arguments.of(
                        "Correlation ID mismatch",
                        "WELCOME_EMAIL", "header-id",
                        "WELCOME_EMAIL", "body-id",
                        "Correlation ID mismatch"
                )
        );
    }

    private PipelineItem createItem(String hTid, String hCid, String bTid, String bCid) {
        var record = TestKafkaConsumerRecordFactory.record().build();
        var item = new PipelineItem(record);

        item.setTemplateIdHeader(hTid);
        item.setCorrelationIdHeader(hCid);

        var payload = new TriggerCommand(
                "test@test.com",
                bTid,
                Map.of(),
                "en",
                1L,
                bCid
        );
        item.setPayload(payload);

        return item;
    }
}