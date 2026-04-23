package com.example.tasktracker.emailsender.o11y.observation.util;

import com.example.tasktracker.emailsender.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpProviderResolver {
    private final AppProperties properties;

    public String resolveName(String host) {
        return properties.getObservability().getDefaultSmtpProviderName();
    }
}
