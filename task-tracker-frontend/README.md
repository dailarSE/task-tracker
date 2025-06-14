# Task Tracker Frontend

Этот модуль содержит статическое одностраничное веб-приложение (SPA) для взаимодействия с бэкендом Task Tracker.
Реализация интерфейса пользователя осуществляется с использованием HTML, CSS, JavaScript и jQuery.

## Структура

- `/index.html`: Основная HTML-страница приложения.
- `/css/style.css`: Основной файл стилей.
- `/js/`: Директория для JavaScript-кода.
  - `api.js`: **Ключевой слой** для взаимодействия с Backend API. Инкапсулирует всю логику AJAX-запросов, добавление JWT-токенов и **централизованную обработку 401 Unauthorized ошибок**.
  - `ui.js`: Слой для управления **общими** UI-компонентами (модальные окна, toast-уведомления) и глобальным состоянием UI.
  - `tasks-ui.js`: UI-хелперы, специфичные для отображения задач.
  - `auth.js`: Слой бизнес-логики для аутентификации.
  - `tasks.js`: Слой бизнес-логики для управления задачами.
  - `main.js`: Основная точка входа. Инициализирует все модули и обработчики событий.

## Архитектура Обработки Ошибок

Приложение использует стандартизированный подход к обработке ошибок на основе **RFC 9457 Problem Details**.

1.  **Централизованная обработка 401 Unauthorized:**
  - Модуль `api.js` содержит обертку `_request`, которая используется для всех защищенных API-вызовов.
  - Эта обертка автоматически перехватывает **любой ответ со статусом 401**.
  - При перехвате 401 ошибки, `api.js` вызывает `auth.handleLogout()` и показывает пользователю соответствующее уведомление (например, "Your session has expired").
  - **Важно:** Ошибка 401 "поглощается" на уровне `api.js` и **не передается** дальше в `.fail()` обработчики слоев бизнес-логики. Это предотвращает "гонку обработчиков" и дублирование кода.

2.  **Обработка остальных ошибок (400, 404, 409, 500 и т.д.):**
  - Эти ошибки **пробрасываются** из `api.js` в слой бизнес-логики (`auth.js`, `tasks.js`).
  - `.fail()` обработчики в этих модулях отвечают за анализ `ProblemDetail` и вызов соответствующих UI-хелперов из `ui.js` для отображения ошибки пользователю (например, подсветки невалидного поля или вывода общего сообщения).

Этот подход обеспечивает четкое разделение ответственности и высокую надежность.

## Запуск (через Docker Compose)

Frontend-приложение раздается веб-сервером Nginx, настроенным в основном `docker-compose.yml` проекта.
Приложение доступно по адресу `http://localhost/` после запуска `docker-compose up`.