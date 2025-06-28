package com.example.tasktracker.scheduler.config;

import com.example.tasktracker.scheduler.client.interceptor.ApiKeyHeaderInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ClientConfig {

    /**
     * Создает и конфигурирует бин RestClient для взаимодействия с Backend API.
     * Использует RestTemplateBuilder для установки базового URL, тайм-аутов и
     * добавления кастомного интерцептора для аутентификации.
     */
    @Bean
    public RestClient backendApiRestClient(
            SchedulerAppProperties properties,
            ApiKeyHeaderInterceptor apiKeyHeaderInterceptor) {

        RestTemplate restTemplate = new RestTemplateBuilder()
                .rootUri(properties.getBackendClient().getUrl())
                .connectTimeout(properties.getBackendClient().getConnectTimeout())
                .readTimeout(properties.getBackendClient().getReadTimeout())
                .additionalInterceptors(apiKeyHeaderInterceptor)
                .build();

        return RestClient.create(restTemplate);
    }
}