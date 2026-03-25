package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.infra.RuntimeInstanceIdProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RuntimeIdentityConfig {

    @Bean
    @ConditionalOnMissingBean
    public RuntimeInstanceIdProvider instanceProvider(AppProperties appProperties) {
        final String instanceId = appProperties.getInstanceId();
        return () -> instanceId;
    }

}
