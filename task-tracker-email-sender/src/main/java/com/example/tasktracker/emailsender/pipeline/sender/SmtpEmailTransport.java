package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;

@RequiredArgsConstructor
@Slf4j
public class SmtpEmailTransport implements EmailTransport {
    private final JavaMailSender mailSender;
    private final EmailSenderProperties properties;
    private final EmailErrorResolver emailErrorResolver;

    /**
     * @param instructions Объект, содержащий получателя, тему и тело письма.
     * @throws InfrastructureException      если обнаружен сбой канала связи или авторизации.
     * @throws RetryableProcessingException если сервер вернул временную ошибку (4xx код).
     * @throws FatalProcessingException     если данные письма некорректны (5xx код или ошибка парсинга).
     */

    @Override
    public void send(SendInstructions instructions) {
        try {
            mailSender.send(createMimeMessage(instructions));
        } catch (Exception e) {
            throw emailErrorResolver.resolve(e);
        }
    }

    @NonNull
    MimeMessage createMimeMessage(SendInstructions instructions) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();

        mimeMessage.setFrom(new InternetAddress(properties.getSenderAddress()));
        mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(instructions.to()));
        mimeMessage.setSubject(instructions.subject(), "UTF-8");

        mimeMessage.setContent(instructions.body(), "text/html; charset=UTF-8");

        instructions.headers().forEach((k, v) -> {
            try {
                mimeMessage.addHeader(k, v);
            } catch (MessagingException e) {
                log.warn("Failed to set header: {}", k);
            }
        });

        return mimeMessage;
    }
}