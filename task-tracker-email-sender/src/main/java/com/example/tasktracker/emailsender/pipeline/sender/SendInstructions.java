package com.example.tasktracker.emailsender.pipeline.sender;

public record SendInstructions(String to, String subject, String body, boolean isHtml, String correlationId) {
}
