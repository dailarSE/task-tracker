package com.example.tasktracker.emailsender.o11y.observation.context;


import com.example.tasktracker.emailsender.o11y.observation.util.EmailTagSanitizer;
import com.example.tasktracker.emailsender.o11y.observation.util.SmtpProviderResolver;
import com.example.tasktracker.emailsender.pipeline.sender.SendInstructions;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpContextFactory {

    private final MailProperties mailProperties;
    private final EmailTagSanitizer sanitizer;
    private final SmtpProviderResolver providerResolver;

    public EmailSendContext createContext(SendInstructions instructions, String senderAddress) {

        String safeDomain = sanitizer.getSafeDomain(instructions.to());

        EmailSendContext context = new EmailSendContext(
                instructions,
                senderAddress,
                mailProperties.getHost(),
                mailProperties.getPort(),
                safeDomain,
                true
        );

        context.setRemoteServiceName(providerResolver.resolveName(mailProperties.getHost()));

        return context;
    }
}
