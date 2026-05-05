package com.example.tasktracker.emailsender.util;

import com.example.tasktracker.emailsender.api.messaging.MessagingHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@TestComponent
public class EmailSupport {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper;
    private final String mailhogApiUrl;

    public EmailSupport(@Value("${test.mailhog.url}") String baseUrl, ObjectMapper mapper) {
        this.mailhogApiUrl = baseUrl + "/api";
        this.mapper = mapper;
    }

    @SneakyThrows
    public List<JsonNode> fetchAllMessages() {
        String raw = restTemplate.getForObject(mailhogApiUrl + "/v2/messages", String.class);
        JsonNode response = mapper.readTree(raw);

        List<JsonNode> items = new ArrayList<>();
        if (response != null && response.has("items")) {
            response.get("items").forEach(items::add);
        }
        return items;
    }

    public static Optional<JsonNode> findByCorrelationId(List<JsonNode> messages, String cid) {
        return messages.stream()
                .filter(m -> {
                    String actualCid = m.path("Content").path("Headers")
                            .path(MessagingHeaders.X_CORRELATION_ID)
                            .path(0).asText();
                    return cid.equals(actualCid);
                })
                .findFirst();
    }

    public static List<JsonNode> findAllByCorrelationId(List<JsonNode> messages, String cid) {
        return messages.stream()
                .filter(m -> {
                    String actualCid = m.path("Content").path("Headers")
                            .path(MessagingHeaders.X_CORRELATION_ID)
                            .path(0).asText();
                    return cid.equals(actualCid);
                })
                .toList();
    }

    public void clear() {
        restTemplate.delete(mailhogApiUrl + "/v1/messages");
    }
}
