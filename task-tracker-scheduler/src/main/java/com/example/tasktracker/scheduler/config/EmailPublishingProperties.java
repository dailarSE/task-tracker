package com.example.tasktracker.scheduler.config;

import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.api.TemplateType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.scheduler.email-publishing")
@Validated
@Getter
@Setter
public class EmailPublishingProperties {
    private ValidityProperties validity = new ValidityProperties();

    @Getter @Setter
    public static class ValidityProperties {
        private Map<TemplateType, Duration> policies = new EnumMap<>(TemplateType.class);
        private Duration defaultDuration = Duration.ofDays(1);

        public Duration getDurationFor(TemplateType type) {
            return policies.getOrDefault(type, defaultDuration);
        }
    }
}
