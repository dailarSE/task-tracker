# Справочник по Ошибкам API (Problem Details)

Этот документ служит официальным реестром всех типов ошибок, возвращаемых API `task-tracker-backend`. Все ответы об ошибках соответствуют стандарту [RFC 9457 (ранее RFC 7807) Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html).

**Базовый URI для `type`:** `https://task-tracker.example.com/probs/`

---

## HTTP 400 - Bad Request

Ошибки, связанные с некорректными данными в запросе клиента.

### `.../probs/validation/method-argument-not-valid`
- **Описание:** Ошибка валидации тела запроса (DTO), аннотированного `@Valid`.
- **Пример `title`:** `Invalid Request Data`
- **Кастомные поля (`properties`):**
  - `invalidParams` (array): Список объектов с деталями по каждому невалидному полю (`field`, `rejectedValue`, `message`).

### `.../probs/validation/constraint-violation`
- **Описание:** Ошибка валидации параметров метода или конфигурационных свойств, аннотированных `@Validated`.
- **Пример `title`:** `Constraint Violation`
- **Кастомные поля (`properties`):**
  - `invalidParams` (array): Список объектов с деталями по каждой нарушенной валидации (`field`, `message`).

### `.../probs/request/parameter-type-mismatch`
- **Описание:** Неверный формат параметра в пути URL или query-строке (например, передана строка вместо числа).
- **Пример `title`:** `Invalid Parameter Format`
- **Кастомные поля (`properties`):**
  - `field` (string): Имя параметра.
  - `rejectedValue` (string): Отклоненное значение.
  - `expectedType` (string): Ожидаемый тип.

### `.../probs/request/body-conversion-error`
- **Описание:** Невозможно распарсить тело запроса (например, синтаксически неверный JSON).
- **Пример `title`:** `Request Data Conversion Error`
- **Кастомные поля (`properties`):** нет.

### `.../probs/user/password-mismatch`
- **Описание:** Пароль и его подтверждение не совпадают при регистрации пользователя.
- **Пример `title`:** `Passwords Do Not Match`
- **Кастомные поля (`properties`):** нет.

---

## HTTP 401 - Unauthorized

Ошибки, связанные с отсутствием или невалидностью аутентификационных данных. Всегда сопровождаются заголовком `WWW-Authenticate: Bearer ...`.

### `.../probs/auth/invalid-credentials`
- **Описание:** Неверный email или пароль при попытке входа.
- **Пример `title`:** `Invalid Credentials`
- **Кастомные поля (`properties`):** нет.

### `.../probs/jwt/expired`
- **Описание:** Предоставленный JWT просрочен.
- **Пример `title`:** `Expired Token`
- **Кастомные поля (`properties`):** нет.

### `.../probs/jwt/invalid-signature`
- **Описание:** Неверная цифровая подпись JWT.
- **Пример `title`:** `Invalid Token Signature`
- **Кастомные поля (`properties`):** нет.

### `.../probs/jwt/malformed`
- **Описание:** Неверный формат или структура JWT.
- **Пример `title`:** `Malformed Token`
- **Кастомные поля (`properties`):** нет.

### `.../probs/jwt/unsupported`
- **Описание:** Неподдерживаемый тип или алгоритм JWT.
- **Пример `title`:** `Unsupported Token`
- **Кастомные поля (`properties`):** нет.

### `.../probs/jwt/empty-or-illegal-argument`
- **Описание:** Пустой или некорректный JWT, содержащий недопустимые аргументы.
- **Пример `title`:** `Invalid Token Content`
- **Кастомные поля (`properties`):** нет.

### `.../probs/jwt/claims-conversion-error`
- **Описание:** Ошибка при извлечении данных пользователя из claims токена.
- **Пример `title`:** `Token Data Error`
- **Кастомные поля (`properties`):** нет.

### `.../probs/unauthorized`
- **Описание:** Общая ошибка, если для доступа к ресурсу требуется полная аутентификация, а она отсутствует.
- **Пример `title`:** `Authentication Required`
- **Кастомные поля (`properties`):** нет.

---

## HTTP 403 - Forbidden

### `.../probs/forbidden`
- **Описание:** У аутентифицированного пользователя нет прав на доступ к ресурсу или выполнение действия.
- **Пример `title`:** `Access Denied`
- **Кастомные поля (`properties`):** нет.

---

## HTTP 404 - Not Found

### `.../probs/task/not-found`
- **Описание:** Запрошенная задача не найдена или пользователь не имеет к ней доступа.
- **Пример `title`:** `Task Not Found`
- **Кастомные поля (`properties`):**
  - `requestedTaskId` (integer): ID запрошенной задачи.
  - `contextUserId` (integer): ID пользователя, в контексте которого производился поиск.

---

## HTTP 409 - Conflict

### `.../probs/user/already-exists`
- **Описание:** Попытка зарегистрировать пользователя с email, который уже существует в системе.
- **Пример `title`:** `User Already Exists`
- **Кастомные поля (`properties`):**
  - `conflictingEmail` (string): Email, вызвавший конфликт.

### `.../probs/resource-conflict` 
- **Описание:** Конфликт при обновлении ресурса из-за конкурентного доступа (оптимистическая блокировка). Возникает, когда предоставленная клиентом версия ресурса не совпадает с версией в базе данных.
- **Пример `title`:** `Resource Conflict`
- **Кастомные поля (`properties`):**
  - `conflictingResourceId` (string или integer): Идентификатор ресурса, который не удалось обновить.

---

## HTTP 500 - Internal Server Error

Ошибки, связанные с проблемами на стороне сервера. Всегда содержат `errorRef` для отслеживания.

### `.../probs/internal/illegal-state`
- **Описание:** Непредвиденная внутренняя ошибка сервера из-за некорректного состояния приложения.
- **Пример `title`:** `Internal Application State Error`
- **Кастомные поля (`properties`):**
  - `errorRef` (string, uuid): Уникальный идентификатор ошибки.

### `.../probs/internal/missing-message-resource`
- **Описание:** Критическая ошибка конфигурации, связанная с отсутствием ключа для сообщения об ошибке в файлах локализации.
- **Пример `title`:** `Internal Configuration Error`
- **Кастомные поля (`properties`):**
  - `errorRef` (string, uuid): Уникальный идентификатор ошибки.
  - `missingResourceInfo` (string): Информация об отсутствующем ключе.