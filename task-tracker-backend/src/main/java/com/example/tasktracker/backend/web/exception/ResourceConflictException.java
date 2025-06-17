package com.example.tasktracker.backend.web.exception;

import com.example.tasktracker.backend.web.ApiConstants;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * Исключение, выбрасываемое при конфликте версий ресурса (оптимистическая блокировка).
 * Устанавливает HTTP-статус 409 Conflict и предоставляет информацию о конфликтующем ресурсе.
 */
@Getter
public class ResourceConflictException extends ErrorResponseException {

    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    public static final String PROBLEM_TYPE_SUFFIX = "resource.conflict";
    public static final String PROBLEM_TYPE_URI_PATH = "resource-conflict";
    public static final String CONFLICTING_RESOURCE_ID_PROPERTY = "conflictingResourceId";

    private final Long conflictingResourceId;

    public ResourceConflictException(@NonNull Long conflictingResourceId) {
        super(STATUS);
        this.conflictingResourceId = conflictingResourceId;
        getBody().setType(URI.create(ApiConstants.PROBLEM_TYPE_BASE_URI + PROBLEM_TYPE_URI_PATH));
        getBody().setProperty(CONFLICTING_RESOURCE_ID_PROPERTY, conflictingResourceId);
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
    public String getTypeMessageCode() {
        return null; // Type URI устанавливается напрямую
    }
}