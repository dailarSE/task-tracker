package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConsistencyFilter implements ItemProcessor {

    @Override
    public void process(PipelineItem item) {
        String templateIdHeader = item.getTemplateIdHeader();
        String templateIdBody = item.getPayload().templateId();

        if (!templateIdHeader.equals(templateIdBody)) {
            reject(item, String.format("Template ID mismatch: header=%s, body=%s", templateIdHeader, templateIdBody));
            return;
        }

        String correlationIdHeader = item.getCorrelationIdHeader();
        String correlationIdBody = item.getPayload().correlationId();
        if (!correlationIdBody.equals(correlationIdHeader)) {
            reject(item, String.format("Correlation ID mismatch: header=%s, body=%s", correlationIdHeader, correlationIdBody));
        }
    }

     void reject(PipelineItem item, String message) {
        item.tryReject(PipelineItem.Status.FAILED, RejectReason.DATA_INCONSISTENCY, message);
        log.debug("Consistency check failed at '{}': {}", item.getCoordinates(), message);
    }
}
