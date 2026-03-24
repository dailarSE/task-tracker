package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class Jsr303Filter implements ItemProcessor {
    private final Validator jsrValidator;

    @Override
    public void process(PipelineItem item) {
        var violations = jsrValidator.validate(item.getPayload());

        if (!violations.isEmpty()) {
            String errorMsg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));

            item.reject(PipelineItem.Status.FAILED, RejectReason.INVALID_PAYLOAD, String.format(errorMsg));
            log.debug("Validation failed for JSON at '{}': {}", item.getCoordinates(), errorMsg);
        }
    }
}
