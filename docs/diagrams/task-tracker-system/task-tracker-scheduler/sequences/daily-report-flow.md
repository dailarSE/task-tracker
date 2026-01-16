# Sequence Diagram: Поток Ежедневного Отчета

**Связанные ADR:**

*   [ADR-0044: Процесс "Ежедневный Отчет" — Этап 1 (Producer)](../../../../adr/scheduler-service/2025-12-18-14-00-процесс-ежедневный-отчет-этап-1.md)
*   [ADR-0045: Процесс "Ежедневный Отчет" — Этап 2 (Consumer)](../../../../adr/scheduler-service/2026-01-07-23-20-процесс-ежедневный-отчет-этап-2-consumer.md)

## Описание Потока

Эта диаграмма визуализирует полный, end-to-end процесс работы сервиса-планировщика по формированию ежедневных отчетов. Процесс разделен на два основных асинхронных этапа:

1.  **Этап 1: Генерация Событий (Producer):** Сервис по расписанию и под распределенной блокировкой (`ShedLock`) итерирует всех пользователей через Backend API. Для каждого пользователя публикуется событие `UserSelectedForDailyReportEvent` в Kafka. Этот процесс является возобновляемым благодаря сохранению состояния (курсора пагинации) в Redis.

2.  **Этап 2: Обработка Событий (Consumer):** Сервис слушает события из Kafka в пакетном режиме. Для каждой пачки событий он делает один групповой запрос к Backend API для обогащения данными (получения отчетов). Затем для каждого полученного отчета он публикует команду `EmailTriggerCommand` в другой топик Kafka, откуда ее заберет сервис `email-sender`.

---

## Диаграмма

```mermaid
sequenceDiagram
    autonumber
    participant Cron as Cron / Trigger
    participant Job as Producer Job
    participant Redis
    participant API as Backend API
    participant KafkaEvents as Kafka (Events)
    participant Cons as Consumer
    participant KafkaCmds as Kafka (Commands)

    note over Cron, KafkaCmds: ЭТАП 1: Генерация событий (Producer)

    Cron->>Job: @Scheduled Trigger
    activate Job
    
    opt Lock Acquired
        Job->>Redis: Запрос на захват ShedLock
        Redis-->>Job: Lock получен
        
        Job->>Redis: Чтение JobState (last_cursor)
        Redis-->>Job: JobState
        
        loop Пока есть данные (Next Page)
            Job->>API: GET /internal/user-ids (cursor)
            activate API
            API-->>Job: List<Long> userIds + nextCursor
            deactivate API
            
            note right of Job: Курсор 'nextCursor' из ответа<br/>используется в следующем запросе.<br/>Цикл завершается, когда<br/>'nextCursor' отсутствует.
            
            par Асинхронная отправка пачки
                Job->>KafkaEvents: Publish UserSelectedForDailyReportEvent (Batch)
            end
            
            Job->>Redis: Сохранить JobState (new_cursor)
            Redis-->>Job: OK
        end
        
        Job->>Redis: Сохранить JobState (PUBLISHED)
        Redis-->>Job: OK
    end
    deactivate Job

    note over Cron, KafkaCmds: ЭТАП 2: Обработка (Consumer)

    KafkaEvents->>Cons: @KafkaListener (Batch of Events)
    activate Cons
    
    Cons->>API: POST /internal/user-reports (List<UserId>)
    activate API
    API-->>Cons: List<UserTaskReport>
    deactivate API
    
    loop Для каждого отчета
        Cons->>Cons: Маппинг в EmailCommand
        Cons->>KafkaCmds: Publish EmailTriggerCommand
    end
    
    Cons-->>KafkaEvents: Commit Offset (Ack Batch)
    deactivate Cons