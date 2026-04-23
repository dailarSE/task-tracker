package com.example.tasktracker.emailsender.pipeline.sender;

import java.util.HashMap;
import java.util.Map;

public record SendInstructions(
        String to,
        String subject,
        String body,
        boolean isHtml,
        Map<String, String> headers
) {
    public SendInstructions {
        headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
    }
}
