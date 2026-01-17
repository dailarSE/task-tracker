package com.example.tasktracker.emailsender.service.model;

/**
 * DTO, содержащий готовый контент письма.
 * Отвязан от специфики транспорта (MIME, SMTP).
 */
public record PreparedEmail(String subject, String body, boolean isHtml) {}