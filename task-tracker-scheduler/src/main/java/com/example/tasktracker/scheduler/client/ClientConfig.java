package com.example.tasktracker.scheduler.client;

import com.example.tasktracker.scheduler.client.interceptor.ApiKeyHeaderInterceptor;
import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

@Configuration
@EnableRetry
public class ClientConfig {

    /**
     * Создает и конфигурирует бин RestClient для взаимодействия с Backend API.
     */
    @Bean
    public RestClient backendApiRestClient(
            SchedulerAppProperties properties,
            ApiKeyHeaderInterceptor apiKeyHeaderInterceptor,
            RestClient.Builder builder) {

        // 1. Настройки таймаутов
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getBackendClient().getConnectTimeout())
                .withReadTimeout(properties.getBackendClient().getReadTimeout());

        // 2. Создаем фабрику (Spring сам найдет HttpComponents, если мы добавили зависимость)
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return builder
                .baseUrl(properties.getBackendClient().getUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(apiKeyHeaderInterceptor)
                .build();
    }
}