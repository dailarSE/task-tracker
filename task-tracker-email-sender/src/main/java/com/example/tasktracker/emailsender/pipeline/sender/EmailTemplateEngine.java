package com.example.tasktracker.emailsender.pipeline.sender;

import java.util.Locale;
import java.util.Map;

public interface EmailTemplateEngine {
    Locale DEFAULT_LOCALE = Locale.ENGLISH;

    RenderingResult process(String templateName, Map<String, Object> context, String localeTag);

    record RenderingResult(String subject, String body) {
    }
}
