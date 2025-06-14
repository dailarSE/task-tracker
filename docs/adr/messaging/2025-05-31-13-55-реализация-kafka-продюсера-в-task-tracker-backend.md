# ADR-0036: Реализация Kafka Продюсера в Сервисе `task-tracker-backend` для Отправки Уведомлений

*   **Статус:** Accepted
*   **Дата:** 2025-05-31
*   **Связанные ADR:**
    *   ADR-MESSAGING-ARCH-001: Общие Принципы и Архитектура Слоя Обмена Сообщениями
    *   ADR-0022: Управление Конфигурационными Свойствами и Безопасность Секретов
    *   ADR-0028: Обновленная Стратегия Обсервабилити
    *   ADR-0020: Компоненты Безопасности
*   **Контекст:**
    *   Сервису `task-tracker-backend` необходимо асинхронно отправлять команды в Kafka (например, для email-уведомлений при регистрации пользователя).
    *   Требуется определить архитектурный паттерн для отправки, который обеспечит надежность (семантика `at-least-once`) и отказоустойчивость, не блокируя при этом основной бизнес-процесс (например, регистрацию пользователя) в случае недоступности Kafka.

*   **Принятое Решение:**
    Принять архитектуру, состоящую из **асинхронного оркестратора отправки** и **механизма персистентного fallback'а** для обработки сбоев.

    1.  **Паттерн Оркестрации Отправки:**
        *   Отправка сообщений в Kafka инкапсулируется в выделенном сервисном компоненте-оркестраторе (например, `EmailNotificationOrchestratorService`).
        *   Основной бизнес-процесс (например, `AuthService`) делегирует этому оркестратору задачу отправки команды, но **не ожидает результата ее выполнения**. Вызов оркестратора должен быть асинхронным (`@Async`) для немедленного возврата управления в основной поток.
        *   Сам оркестратор использует стандартный `KafkaTemplate` для фактического взаимодействия с Kafka.

    2.  **Обработка Результата Отправки:**
        *   Результат отправки сообщения (`SendResult` или `Exception`), возвращаемый `KafkaTemplate` асинхронно (через `CompletableFuture`), обрабатывается в коллбэке.
        *   Коллбэк выполняется на выделенном, управляемом пуле потоков (`Executor`) для изоляции от I/O потоков Kafka-клиента и HTTP-потоков.

    3.  **Механизм Fallback при Сбое Доставки:**
        *   **Принцип:** Для обеспечения семантики `at-least-once`, если `KafkaTemplate` сообщает о невозможности доставить сообщение (после всех внутренних ретраев), команда **не должна быть потеряна**.
        *   **Реализация:** При получении ошибки доставки в асинхронном коллбэке, оркестратор **сохраняет** информацию о неотправленной команде в отдельную, персистентную таблицу в базе данных (например, `undelivered_email_commands`).
        *   **Транзакционная Изоляция:** Операция сохранения в fallback-таблицу выполняется в **новой, независимой транзакции** (`Propagation.REQUIRES_NEW`), чтобы гарантировать ее атомарность и независимость от состояния любых других транзакций.

    4.  **Отказоустойчивость Бизнес-Процесса:**
        *   Основной бизнес-процесс (например, регистрация пользователя) **не должен прерываться или откатываться** из-за проблем с отправкой сообщения в Kafka.
        *   Это достигается за счет асинхронного вызова оркестратора и обработки всех потенциальных синхронных ошибок этого вызова (например, переполнение очереди executor'а) через `try-catch` без пробрасывания исключения дальше.

    5.  **Структура Сообщения (Команды):**
        *   Для команд используется стандартизированный DTO (например, `EmailTriggerCommand`), содержащий все необходимые данные для получателя. Включает поля для корреляции (`correlationId`) и локализации (`locale`).

*   **Рассмотренные Альтернативы:**
    *   **Простая отправка "Fire-and-Forget":** Отвергнуто, так как не обеспечивает `at-least-once` и приводит к потере сообщений при сбоях Kafka.
    *   **Синхронная отправка:** Отвергнуто, так как блокирует основной бизнес-поток и делает его зависимым от доступности Kafka.
    *   **Полноценный Outbox-паттерн:** Отложен как более сложное решение, требующее изменений в транзакциях бизнес-операций. Текущий fallback-механизм является более простым и достаточным для не самых критичных событий.

*   **Последствия:**
    *   Архитектура обеспечивает надежную доставку команд по принципу "как минимум один раз".
    *   Бизнес-операции развязаны с процессом отправки сообщений, что повышает отказоустойчивость системы в целом.
    *   Вводится дополнительная сложность в виде сервиса-оркестратора и fallback-таблицы.
    *   Создается основа для будущего механизма переотправки (retry) сообщений из fallback-таблицы (например, через шедулер).
    *   Требуется идемпотентность на стороне сервиса-консьюмера для обработки возможных дубликатов, возникающих при `at-least-once`.