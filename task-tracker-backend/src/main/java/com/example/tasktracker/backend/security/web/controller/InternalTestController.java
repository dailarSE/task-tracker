package com.example.tasktracker.backend.security.web.controller;

import com.example.tasktracker.backend.security.apikey.ApiKeyAuthentication;
import com.example.tasktracker.backend.web.ApiConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Временный контроллер для тестирования безопасности внутренних эндпоинтов.
 * Этот контроллер должен быть удален или отключен в production-профиле.
 */
@RestController
@RequestMapping(ApiConstants.API_V1_PREFIX + "/internal")
@Slf4j
public class InternalTestController {

    /**
     * Простой эндпоинт, который должен быть доступен только с валидным API-ключом.
     * Возвращает ID сервиса и ID экземпляра, извлеченные из Authentication.
     * @param apiKeyAuth Объект аутентификации, установленный фильтром.
     * @return Ответ 200 OK с данными аутентификации.
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testInternalEndpoint(ApiKeyAuthentication apiKeyAuth) {

        if (apiKeyAuth == null || !apiKeyAuth.isAuthenticated()) {
            return ResponseEntity.status(500).body(Map.of("error", "Authentication object not found or not authenticated"));
        }

        log.info("testInternalEndpoint called. ServiceId: {}, InstanceId: {}", apiKeyAuth.getServiceId(), apiKeyAuth.getInstanceId());
        return ResponseEntity.ok(Map.of(
                "message", "Internal API endpoint reached successfully.",
                "serviceId", apiKeyAuth.getServiceId(),
                "instanceId", apiKeyAuth.getInstanceId()
        ));
    }
}