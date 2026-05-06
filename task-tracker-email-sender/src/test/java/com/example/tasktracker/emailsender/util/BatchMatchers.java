package com.example.tasktracker.emailsender.util;

import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import lombok.experimental.UtilityClass;
import org.mockito.ArgumentMatcher;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.argThat;

@UtilityClass
public class BatchMatchers {

    /**
     * Матчер для Mockito: проверяет, что батч содержит хотя бы один элемент с указанным CID.
     */
    public static PipelineBatch containsCid(String cid) {
        return argThat(new ArgumentMatcher<>() {
            @Override
            public boolean matches(PipelineBatch batch) {
                if (batch == null || batch.items() == null) return false;
                return batch.items().stream()
                        .map(PipelineItem::getPayload)
                        .filter(Objects::nonNull)
                        .anyMatch(p -> cid.equals(p.correlationId()));
            }

            @Override
            public String toString() {
                return "[PipelineBatch containing item with correlationId=" + cid + "]";
            }
        });
    }
}