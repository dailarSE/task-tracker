package com.example.tasktracker.backend.security.web.controller;

import com.example.tasktracker.backend.web.ApiConstants;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Временный контроллер для тестирования безопасности внутренних эндпоинтов.
 * Этот контроллер должен быть удален или отключен в production-профиле.
 */
@RestController
@RequestMapping(ApiConstants.API_V1_PREFIX + "/internal")
@Profile({"dev","ci"})
public class InternalTestController {

    /**
     * Простой эндпоинт, который должен быть доступен только с валидным API-ключом.
     * @return Ответ 200 OK с простым сообщением.
     */
    @GetMapping("/test")
    public ResponseEntity<String> testInternalEndpoint() {
        return ResponseEntity.ok("Internal API endpoint reached successfully.");
    }
}