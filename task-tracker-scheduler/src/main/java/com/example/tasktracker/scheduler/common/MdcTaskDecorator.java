package com.example.tasktracker.scheduler.common;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * Декоратор, который захватывает MDC-контекст из вызывающего потока
 * и восстанавливает его в потоке выполнения задачи.
 */
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // Захватываем контекст в момент создания задачи
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                // Восстанавливаем контекст в момент выполнения
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                // Очищаем контекст после выполнения, чтобы не загрязнять поток из пула
                MDC.clear();
            }
        };
    }
}