package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TtlFilterTest {

    private static final Instant NOW = Instant.parse("2025-01-01T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));

    private TtlFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TtlFilter(fixedClock);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTtlScenarios")
    @DisplayName("TTL Expiry Logic: Boundary and typical cases")
    void shouldEnforceTtlExpiry(String description, Instant deadline, PipelineItem.Status expectedStatus, RejectReason expectedReason) {
        var item = createItemWithDeadline(deadline);

        filter.process(item);

        assertEquals(expectedStatus, item.getStage().status(), "Status mismatch for: " + description);
        assertEquals(expectedReason, item.getStage().rejectReason(), "Reject reason mismatch for: " + description);
    }

    private static Stream<Arguments> provideTtlScenarios() {
        return Stream.of(
                Arguments.of("Future deadline (1s) - valid", NOW.plus(Duration.ofSeconds(1)), PipelineItem.Status.PENDING, RejectReason.NONE),
                Arguments.of("Far future (100d) - valid", NOW.plus(Duration.ofDays(100)), PipelineItem.Status.PENDING, RejectReason.NONE),

                Arguments.of("Exact deadline (now) - valid", NOW, PipelineItem.Status.PENDING, RejectReason.NONE),

                Arguments.of("Expired just now (-1ns) - skipped", NOW.minus(Duration.ofNanos(1)), PipelineItem.Status.SKIPPED, RejectReason.TTL_EXPIRED),
                Arguments.of("Expired long ago (-1h) - skipped", NOW.minus(Duration.ofHours(1)), PipelineItem.Status.SKIPPED, RejectReason.TTL_EXPIRED)
        );
    }

    private PipelineItem createItemWithDeadline(Instant deadline) {
        var record = TestKafkaConsumerRecordFactory.record().build();
        var item = new PipelineItem(record);
        item.setDeadline(deadline);
        return item;
    }
}