package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static com.example.tasktracker.emailsender.api.email.EmailHeaders.X_CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpEmailTransportTest {
    private MimeMessage mimeMessage;
    private JavaMailSender mailSender;
    private EmailSenderProperties props;
    private EmailErrorResolver errorResolver;

    private SmtpEmailTransport transport;

    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage((Session) null);
        mailSender = new JavaMailSenderImpl(){
            @Override
            public MimeMessage createMimeMessage() {
                return mimeMessage;
            }

            @Override
            public void send(MimeMessage mimeMessage) throws MailException {
                return;
            }
        };
        props = new EmailSenderProperties();
        props.setSenderAddress("test@test");
        errorResolver = new EmailErrorResolver();

        transport = new SmtpEmailTransport(mailSender, props, errorResolver);
    }

    @Test
    void shouldAddCorrelationIdToMimeHeaders() throws Exception {
        var instructions = new SendInstructions(
                "test@test.com", "Subject", "Body", true, "corr-123"
        );

        transport.send(instructions);

        String header = mimeMessage.getHeader(X_CORRELATION_ID, null);
        assertEquals("corr-123", header, "X-Correlation-ID header must be present in MimeMessage");
    }
}