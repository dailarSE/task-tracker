package com.example.tasktracker.emailsender.service.component;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.service.model.PreparedEmail;
import io.opentelemetry.api.trace.Span;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender {

    private final JavaMailSender mailSender;
    private final EmailSenderProperties properties;

    /**
     * Отправляет подготовленное письмо через SMTP.
     * Добавляет заголовки трассировки.
     */
    public void send(String to, PreparedEmail content) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            helper.setFrom(properties.getSenderAddress());
            helper.setTo(to);
            helper.setSubject(content.subject());
            helper.setText(content.body(), content.isHtml());

            injectCorrelationHeader(helper.getMimeMessage());

            mailSender.send(mimeMessage);

            log.info("Email sent to '{}'. Subject: '{}'.", to, content.subject());

        } catch (MessagingException e) {
            log.error("Failed to create MIME message for '{}'. ", to);
            throw new MailSendException("Failed to create MIME message", e);
        } catch (MailException e) {
            log.error("Failed to send email via SMTP to '{}'. ", to);
            throw e; // Пробрасываем для обработки (retry)
        }
    }

    private void injectCorrelationHeader(MimeMessage message) throws MessagingException {
        String traceId;
        if (Span.current().getSpanContext().isValid()) {
            traceId = Span.current().getSpanContext().getTraceId();
        } else {
            traceId = UUID.randomUUID().toString();
            log.warn("Could not find a valid correlation ID. Generated fallback correlation ID: {}", traceId);
        }

        message.addHeader("X-Correlation-ID", traceId);
    }
}