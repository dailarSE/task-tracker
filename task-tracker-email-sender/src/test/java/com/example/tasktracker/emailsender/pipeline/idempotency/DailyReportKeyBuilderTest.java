package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DailyReportKeyBuilderTest {

    private final DailyReportKeyBuilder builder = new DailyReportKeyBuilder();

    @Test
    @DisplayName("Build: should include reportDate from context in the key")
    void shouldIncludeDateInKey() {
        var command = new TriggerCommand(
                "test@test.com", "DAILY_TASK_REPORT",
                Map.of("reportDate", "2025-01-01"),
                "en", 456L, "c-1"
        );

        String result = builder.build(command);

        assertEquals("email:dedup:daily_task_report:456:2025-01-01", result);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidContexts")
    @DisplayName("Fail-Fast: should throw exception when reportDate is missing or invalid")
    void shouldThrowWhenDateIsMissing(Map<String, Object> context) {
        var command = new TriggerCommand("t@t.com", "DAILY_REPORT", context, "en", 1L, "c-1");

        var ex = assertThrows(IllegalArgumentException.class, () -> builder.build(command));
        assertTrue(ex.getMessage().contains("reportDate"));
    }

    private static Stream<Arguments> provideInvalidContexts() {
        return Stream.of(
                Arguments.of(Map.of()),
                Arguments.of(Collections.singletonMap("reportDate", " ")),
                Arguments.of(Collections.singletonMap("other", "val"))
        );
    }
}