package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPAddressSucceededException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

import java.net.SocketException;
import java.net.SocketTimeoutException;

@Component
@Slf4j
public class EmailErrorResolver {

    /**
     * Классифицирует ошибки SMTP/IO в доменные исключения:
     * <ul>
     *  <li>{@link InfrastructureException} (INFRASTRUCTURE): Сеть, Auth, 42x коды.</li>
     *  <li>{@link RetryableProcessingException} (REMOTE_ERROR): Временные SMTP 4xx коды.</li>
     *  <li>{@link FatalProcessingException}: Ошибки данных/шаблонов (5xx, INVALID_PAYLOAD) или системные баги (INTERNAL_ERROR).</li>
     * </ul>
     */
    public RuntimeException resolve(Throwable t) {

        // Ошибки авторизации и физического коннекта (Infrastructure)
        if (t instanceof MailConnectException || t instanceof MailAuthenticationException) {
            log.debug("Infrastructure failure detected: {}.", t.getClass().getSimpleName());
            return new InfrastructureException("SMTP connection or authentication failure", t);
        }

        // Ошибки подготовки (Poison Pill в данных или шаблоне)
        if (t instanceof MailPreparationException || t instanceof MailParseException) {
            log.debug("Fatal data/template error: {}.", t.getMessage());
            return new FatalProcessingException(RejectReason.INVALID_PAYLOAD, "Email content preparation failed", t);
        }
        if (t instanceof MessagingException) {
            log.debug("Exception during message preparation: {}. ", t.getMessage());
            return new FatalProcessingException(RejectReason.INVALID_PAYLOAD, "MIME message creation failed", t);
        }

        // Разбор SMTP ответов сервера (MailSendException)
        if (t instanceof MailSendException mse) {
            return resolveMailSendException(mse);
        }

        // Default: считаем фатальной.
        log.error("UNCLASSIFIED EXCEPTION: {}. Message: {}.", t.getClass().getName(), t.getMessage(), t);
        return new FatalProcessingException(RejectReason.INTERNAL_ERROR, "Unclassified processing error", t);
    }

    private RuntimeException resolveMailSendException(MailSendException mse) {
        Exception cause = mse.getFailedMessages().values().stream()
                .findFirst()
                .orElse(mse);

        Integer smtpCode = switch (cause) {
            case SMTPSendFailedException e -> e.getReturnCode();
            case SMTPAddressFailedException e -> e.getReturnCode();
            case SMTPSenderFailedException e -> e.getReturnCode();
            case SMTPAddressSucceededException e -> e.getReturnCode();
            default -> null;
        };

        if (smtpCode != null) {
            return classifyBySmtpCode(smtpCode, cause);
        }

        if (isConnectionRelated(cause)) {
            log.debug("Network/IO failure without SMTP code: {}.", cause.getMessage());
            return new InfrastructureException("SMTP I/O failure", cause);
        }

        log.debug("Generic SMTP failure: {}.", cause.getMessage());
        return new InfrastructureException("Generic SMTP failure", cause);
    }

    /**
     * RFC 5321 Section 4.2.1
     */
    private RuntimeException classifyBySmtpCode(int code, Exception cause) {
        int firstDigit = code / 100;
        int secondDigit = (code / 10) % 10;

        log.trace("Classifying SMTP response code: {}", code);

        return switch (firstDigit) {
            case 4 -> {
                // 4yz: Transient Negative (Временная ошибка)
                // RFC 5321: x2z - Connection related (например 421 - Service shutting down)
                if (secondDigit == 2) {
                    log.debug("SMTP Infrastructure Transient Error ({}). SUSPEND suggested.", code);
                    yield new InfrastructureException("Infrastructure SMTP 42x error: " + code, cause);
                }
                // Остальные 4xx (450 - Mailbox busy и т.д.) - шлем в ретрай-топик
                log.debug("SMTP 4xx (Transient) error: {}.", code);
                yield new RetryableProcessingException(RejectReason.REMOTE_ERROR, "Transient SMTP error: " + code, cause);
            }
            case 5 -> {
                // 5yz: Permanent Negative (DLT)
                // Это "Ядовитое письмо" (например 550 - User not found, 501 - Syntax error)
                log.debug("SMTP Permanent Error ({}). Sending to DLT.", code);
                yield new FatalProcessingException(RejectReason.INVALID_PAYLOAD, "Permanent SMTP error: " + code, cause);
            }
            default -> {
                // Неожиданные коды (2xx, 3xx не должны быть здесь)
                log.debug("Unexpected SMTP code: {}.", code);
                yield new InfrastructureException("Unexpected SMTP response: " + code, cause);
            }
        };
    }

    private boolean isConnectionRelated(Throwable t) {
        return t instanceof SocketException ||
                t instanceof SocketTimeoutException ||
                (t.getCause() != null && isConnectionRelated(t.getCause()));
    }
}
