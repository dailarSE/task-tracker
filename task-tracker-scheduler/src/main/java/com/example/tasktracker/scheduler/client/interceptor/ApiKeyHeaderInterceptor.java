package com.example.tasktracker.scheduler.client.interceptor;

import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class ApiKeyHeaderInterceptor implements ClientHttpRequestInterceptor {

    public static final String API_KEY_HEADER_NAME = "X-API-Key";
    public static final String INSTANCE_ID_HEADER_NAME = "X-Service-Instance-Id";

    private final String apiKey;
    private final String instanceId;

    public ApiKeyHeaderInterceptor(SchedulerAppProperties properties) {
        this.apiKey = properties.getBackendClient().getApiKey();
        this.instanceId = UUID.randomUUID().toString();
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(HttpRequest request, byte @NonNull [] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        headers.set(API_KEY_HEADER_NAME, apiKey);
        headers.set(INSTANCE_ID_HEADER_NAME, instanceId);
        return execution.execute(request, body);
    }
}