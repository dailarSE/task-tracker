package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;

public interface EmailTransport {
    /**
     * @throws InfrastructureException      если обнаружен сбой канала связи или авторизации.
     * @throws RetryableProcessingException если сервер вернул временную ошибку (4xx код).
     * @throws FatalProcessingException     если данные письма некорректны (5xx код или ошибка парсинга).
     */
    void send(SendInstructions sendInstructions);
}
