package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.api.messaging.MessagingHeaders;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CorrelationIdFilter implements ItemProcessor{
    @Override
    public void process(PipelineItem item) {
        if (item.getCorrelationIdHeader() == null || item.getCorrelationIdHeader().isBlank()) {
            String message = "Missing mandatory header: " + MessagingHeaders.X_CORRELATION_ID;
            item.reject(PipelineItem.Status.FAILED, RejectReason.MALFORMED_TRANSPORT, message);
            log.debug("Validation failed for {}: {}", item.getCoordinates(), message);
        }
    }
}
