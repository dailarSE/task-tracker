# ADR-0036: Реализация Kafka Продюсера в Сервисе `task-tracker-backend` для Отправки Уведомлений

*   **Статус:** Accepted
*   **Дата:** 2025-05-31
*   **Связанные ADR:**
    *   ADR-MESSAGING-ARCH-001: Общие Принципы и Архитектура Слоя Обмена Сообщениями
    *   ADR-0022: Управление Конфигурационными Свойствами и Безопасность Секретов
    *   ADR-0028: Обновленная Стратегия Обсервабилити
    *   ADR-0020: Компоненты Безопасности
*   **Контекст:**
    *   В рамках задачи TT5 и User Story US1/2, сервис `task-tracker-backend` должен отправлять асинхронное сообщение в Kafka при успешной регистрации нового пользователя. Это сообщение предназначено для сервиса `task-tracker-email-sender`.
    *   Необходимо определить конкретную реализацию Kafka-продюсера в `task-tracker-backend`, включая конфигурацию, структуру сообщения, семантику доставки и интеграцию с существующими компонентами, в соответствии с `ADR-0035`.

*   **Принятое Решение:**

    1.  **Библиотека Интеграции:** Используется `spring-kafka` и `org.springframework.kafka.core.KafkaTemplate` для отправки сообщений, как определено в `ADR-0035`.

    2.  **DTO Сообщения (Команда для Email Sender'а):**
        *   Используется DTO `EmailTriggerCommandDto` (или аналогичное имя, отражающее команду/событие для запуска email):
            ```java
            // package com.example.tasktracker.backend.user.messaging.dto;
            public class EmailTriggerCommandDto {
                private String recipientEmail;
                private String templateId; // e.g., "USER_WELCOME"
                private Map<String, Object> templateContext; // e.g., {"userEmail": "...", "userName": "..."}
                private String locale; // Опционально, для локализации письма Email Sender'ом
                private String userId; // Для логирования/трассировки
                private String correlationId; // Для сквозной трассировки
            }
            ```
        *   DTO сериализуется в JSON.

    3.  **Конфигурация Продюсера (`application.yml` для `task-tracker-backend`):**
        *   **Глобальные `spring.kafka.producer.*`:** Настраиваются базовые параметры (bootstrap-servers, key/value serializers, trusted.packages, add.type.headers=false).
        *   **Семантика доставки для приветственных уведомлений:** `At-least-once`.
            *   Это будет **дефолтная конфигурация для автоконфигурируемого `KafkaTemplate`**.
            *   `acks`: `all`.
            *   `retries`: (например, `3`).
            *   `properties.delivery.timeout.ms`: (например, `120000`).
            *   `properties.enable.idempotence`: `false`.
        *   **Примечание:** Если в будущем потребуются другие семантики для других типов сообщений из этого же сервиса, будут созданы кастомные бины `KafkaTemplate` с соответствующими `ProducerFactory`.

    4.  **Сервис для Отправки Сообщений (`KafkaProducerService`):**
        *   Создается `com.example.tasktracker.backend.kafka.producer.KafkaProducerService`.
        *   Инжектируется автоконфигурируемый `KafkaTemplate<String, EmailTriggerCommandDto>`.
        *   Метод `sendEmailTriggerCommand(EmailTriggerCommandDto commandDto, String topicName)`:
            *   Использует `kafkaTemplate.send(topicName, key, commandDto).whenCompleteAsync(...)`.
            *   Ключ сообщения (`key`): `commandDto.getRecipientEmail()` или `commandDto.getUserId()`.
            *   Асинхронно логирует результат отправки (успех/ошибка) с `correlationId`.

    5.  **Интеграция с `AuthService` (для US1-Часть2):**
        *   `AuthService` инжектирует `KafkaProducerService`.
        *   После успешного сохранения пользователя, `AuthService` формирует `EmailTriggerCommandDto` (с `templateId="USER_WELCOME"`, контекстом, содержащим email пользователя, и, возможно, его ID и переданной локалью из запроса на регистрацию, если она есть) и вызывает `kafkaProducerService.sendEmailTriggerCommand(...)`.
        *   Отправка сообщения в Kafka не блокирует основной поток и не вызывает откат транзакции регистрации при ошибке отправки в Kafka (реализуется через `try-catch` или не пробрасывание исключения из `whenCompleteAsync`). Ошибка отправки логируется как `ERROR`.

    6.  **Именование Топика:**
        *   Используется топик: `task_tracker.notifications.email_commands` (или `task_tracker.email.v1.triggers` для большей специфичности команды).

    7.  **Наблюдаемость (Observability):**
        *   **Трассировка:** Авто-инструментация `spring-kafka` обеспечивает создание спанов и распространение контекста.
        *   **Логирование:** Операции логируются с `trace_id`, `correlationId`, `userId`/`email`.
        *   **Метрики:** Собираются стандартные метрики `spring-kafka-producer`.

*   **Рассмотренные Альтернативы (для отправки из `AuthService`):**
    *   **`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`:** Рекомендовано как улучшение в `ADR-MESSAGING-ARCH-001` для гарантии отправки только после коммита БД. Для текущей задачи TT5 принято упрощение с прямой отправкой и локальной обработкой ошибок для скорости реализации. Это будет пересмотрено при внедрении Outbox паттерна или для более критичных событий.

*   **Последствия:**
    *   Реализован механизм асинхронной отправки команд на генерацию email-уведомлений.
    *   Получен практический опыт настройки и использования `KafkaTemplate` с JSON DTO.
    *   Заложена основа для расширения использования Kafka в `task-tracker-backend`.
    *   Обеспечена базовая наблюдаемость процесса отправки.