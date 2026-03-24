package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
public class TtlFormatProcessor implements ItemProcessor {
    private final EmailSenderProperties.MessageValidityProperties props;

    public TtlFormatProcessor(EmailSenderProperties emailSenderProperties) {
        this.props = emailSenderProperties.getMessageValidity();
    }

    @Override
    public void process(PipelineItem item) {
        String validUntilHeader = item.getValidUntilHeader();
        if (validUntilHeader != null) {
            try {
                item.setDeadline(Instant.parse(validUntilHeader));
            } catch (DateTimeParseException ex) {
                String message = "Invalid TTL Header format";
                item.reject(PipelineItem.Status.FAILED, RejectReason.DATA_INCONSISTENCY, message, ex);
                log.debug("Processing failed for {}: '{}'", item.getCoordinates(), message);
            }
        } else
            item.setDeadline(calculateDeadline(item));
    }

    private Instant calculateDeadline(PipelineItem item) {
        long recordTimestamp = item.getOriginalRecord().timestamp();
        TemplateType templateType = item.getTemplateType();
        Duration ttl = props.getDurationFor(templateType);

        return Instant.ofEpochMilli(recordTimestamp).plus(ttl);

    }
}