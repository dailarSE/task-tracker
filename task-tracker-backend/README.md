# Task Tracker Backend Service

Этот сервис является ядром приложения "Task Tracker". Он реализует REST API для управления пользователями и задачами, а также отвечает за основную бизнес-логику и взаимодействие с базой данных и брокером сообщений.

## Основной функционал

- Регистрация и аутентификация пользователей (JWT).
- CRUD операции для задач.
- Публикация событий в Kafka (например, при регистрации пользователя).

## API

(TBD: OpenAPI/Swagger документация)


## Архитектура Сервиса

Внутренняя архитектура сервиса описана с помощью диаграмм C4 Model.

### L3: Компоненты Подсистемы Безопасности

*   **Поток Аутентификации:** [C4 Level 3: Component Diagram (Backend - Authentication Flow)](../docs/diagrams/task-tracker-system/backend-api/c4-L3-authentication-flow.webp)
*   **Поток Авторизации и Доступа к Данным:** [C4 Level 3: Component Diagram (Backend - Authorization Flow)](../docs/diagrams/task-tracker-system/backend-api/c4-L3-authorization-flow.webp) 