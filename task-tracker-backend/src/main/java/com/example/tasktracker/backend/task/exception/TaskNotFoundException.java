package com.example.tasktracker.backend.task.exception;

import com.example.tasktracker.backend.web.ApiConstants;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * Исключение, выбрасываемое, когда задача не найдена для текущего пользователя
 * или вообще не существует.
 * <p>
 * Устанавливает HTTP-статус 404 Not Found и предоставляет локализуемые
 * сообщения. В 'properties' ответа добавляются машиночитаемые поля
 * {@value #REQUESTED_TASK_ID_PROPERTY} и (опционально) {@value #CONTEXT_USER_ID_PROPERTY}
 * для улучшения диагностики.
 *
 * @see ErrorResponseException
 * @see com.example.tasktracker.backend.web.exception.GlobalExceptionHandler
 */
@Getter
public class TaskNotFoundException extends ErrorResponseException {

    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;
    public static final String PROBLEM_TYPE_SUFFIX = "task.notFound";
    public static final String PROBLEM_TYPE_URI_PATH = "task/not-found";

    /**
     * Ключ для поля в 'properties', содержащего ID запрошенной, но не найденной задачи.
     */
    public static final String REQUESTED_TASK_ID_PROPERTY = "requestedTaskId";
    /**
     * Ключ для поля в 'properties', содержащего ID пользователя, в контексте которого производился поиск.
     */
    public static final String CONTEXT_USER_ID_PROPERTY = "contextUserId";

    private final Long requestedTaskId;
    private final Long currentUserId;

    /**
     * Конструктор для случая, когда задача не найдена для конкретного пользователя.
     *
     * @param requestedTaskId ID запрошенной задачи.
     * @param currentUserId   ID текущего пользователя, для которого задача искалась.
     */
    public TaskNotFoundException(Long requestedTaskId, Long currentUserId) {
        this(requestedTaskId, currentUserId, null);
    }

    /**
     * Конструктор с причиной.
     *
     * @param requestedTaskId ID запрошенной задачи.
     * @param currentUserId   ID текущего пользователя.
     * @param cause           Исходная причина.
     */
    public TaskNotFoundException(@NonNull Long requestedTaskId, @Nullable Long currentUserId, @Nullable Throwable cause) {
        super(STATUS, cause);
        this.requestedTaskId = requestedTaskId;
        this.currentUserId = currentUserId;

        getBody().setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + PROBLEM_TYPE_URI_PATH));

        getBody().setProperty(REQUESTED_TASK_ID_PROPERTY, requestedTaskId);

        if (currentUserId != null) {
            getBody().setProperty(CONTEXT_USER_ID_PROPERTY, currentUserId);
        }
    }

    @Override
    public String getTitleMessageCode() {
        return "problemDetail." + PROBLEM_TYPE_SUFFIX + ".title";
    }

    @Override
    public String getDetailMessageCode() {
        return "problemDetail." + PROBLEM_TYPE_SUFFIX + ".detail";
    }
    
    @Override
    public Object[] getDetailMessageArguments() {
        return new Object[]{this.requestedTaskId};
    }

    @Override
    public String getTypeMessageCode() {
        return null; // Type URI устанавливается напрямую
    }
}