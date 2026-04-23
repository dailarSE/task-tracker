package com.example.tasktracker.emailsender.o11y.observation.util;

import com.example.tasktracker.emailsender.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class EmailTagSanitizer {

    private final AppProperties properties;

    public String getSafeDomain(String email) {
        Set<String> knownDomains = properties.getObservability().getKnownDomains();
        if (email == null || !email.contains("@")) return "invalid";

        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        return knownDomains.contains(domain) ? domain : "other_domain";
    }
}
