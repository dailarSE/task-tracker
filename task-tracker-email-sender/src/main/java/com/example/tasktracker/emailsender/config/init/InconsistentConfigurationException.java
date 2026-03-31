package com.example.tasktracker.emailsender.config.init;

import lombok.Getter;

/**
 * Выбрасывается при обнаружении критических противоречий в настройках.
 */
@Getter
public class InconsistentConfigurationException extends RuntimeException {
    private final String action;

    public InconsistentConfigurationException(String message, String action) {
        super(message);
        this.action = action;
    }

}
