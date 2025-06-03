package com.example.tasktracker.backend.common;

import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Простой {@link TaskDecorator} для копирования MDC из вызывающего потока
 * в поток, выполняющий задачу.
 */
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // Захватываем MDC из текущего (вызывающего) потока
        final Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            // Восстанавливаем MDC в потоке выполнения задачи
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                runnable.run();
            } finally {
                // Очищаем MDC после выполнения задачи
                MDC.clear();
            }
        };
    }
}
