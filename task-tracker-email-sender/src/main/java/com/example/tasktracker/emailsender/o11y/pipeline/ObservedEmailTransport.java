package com.example.tasktracker.emailsender.o11y.pipeline;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.o11y.observation.context.EmailSendContext;
import com.example.tasktracker.emailsender.o11y.observation.context.SmtpContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.convention.EmailSmtpConvention;
import com.example.tasktracker.emailsender.pipeline.sender.EmailTransport;
import com.example.tasktracker.emailsender.pipeline.sender.SendInstructions;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ObservedEmailTransport implements EmailTransport {
    private final EmailTransport delegate;
    private final ObservationRegistry registry;
    private final EmailSmtpConvention convention;
    private final SmtpContextFactory contextFactory;
    private final EmailSenderProperties emailSenderProperties;

    @Override
    public void send(SendInstructions sendInstructions) {
        EmailSendContext sendContext =
                contextFactory.createContext(sendInstructions, emailSenderProperties.getSenderAddress());

        Observation.createNotStarted(convention, () -> sendContext, registry)
                .observe(() -> delegate.send(sendInstructions));
    }
}
