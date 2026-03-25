package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Реестр для поиска подходящей стратегии генерации ключа.
 */
@Component
@Slf4j
public class TemplateKeyRegistry {
    private final Map<TemplateType, TemplateKeyBuilder> registry;

    public TemplateKeyRegistry(List<TemplateKeyBuilder> builders) {
        Map<TemplateType, TemplateKeyBuilder> map = new EnumMap<>(TemplateType.class);

        for (TemplateType type : TemplateType.values()) {
            for (TemplateKeyBuilder builder : builders) {
                if (builder.supports(type)) {
                    map.put(type, builder);
                    break;
                }
            }
        }

        if (map.size() < TemplateType.values().length) {
            var missing = Arrays.stream(TemplateType.values())
                    .filter(type -> !map.containsKey(type))
                    .toList();

            String errorMsg = "TemplateKeyRegistry initialization failed. Missing builders for types: " + missing;
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        log.debug("TemplateKeyRegistry successfully initialized with {} builders.", map.size());
        this.registry = Collections.unmodifiableMap(map);
    }

    /**
     * Возвращает стратегию для указанного типа шаблона.
     *
     * @param type тип шаблона.
     * @return подходящая реализация {@link TemplateKeyBuilder}.
     */
    public TemplateKeyBuilder forType(@NonNull TemplateType type) {
        return registry.get(type);
    }
}