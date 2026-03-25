package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateKeyRegistryTest {

    @Test
    @DisplayName("Startup: should succeed when all TemplateTypes have builders")
    void shouldInitializeWhenAllBuildersPresent() {
        var builders = List.of(new UserWelcomeKeyBuilder(), new DailyReportKeyBuilder());

        var registry = assertDoesNotThrow(() -> new TemplateKeyRegistry(builders));

        assertNotNull(registry.forType(TemplateType.USER_WELCOME));
        assertNotNull(registry.forType(TemplateType.DAILY_TASK_REPORT));
    }

    @Test
    @DisplayName("Fail-Fast: should throw IllegalStateException on startup if any builder is missing")
    void shouldFailWhenBuilderIsMissing() {
        List<TemplateKeyBuilder> incompleteBuilders = List.of(new UserWelcomeKeyBuilder());

        var ex = assertThrows(IllegalStateException.class, () -> new TemplateKeyRegistry(incompleteBuilders));

        assertTrue(ex.getMessage().contains(TemplateType.DAILY_TASK_REPORT.toString()));
    }
}