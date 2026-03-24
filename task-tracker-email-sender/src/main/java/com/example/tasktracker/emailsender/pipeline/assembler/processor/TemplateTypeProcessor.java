package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class TemplateTypeProcessor implements ItemProcessor {
    @Override
    public void process(PipelineItem item) {
        String templateIdHeader = item.getTemplateIdHeader();

        if (templateIdHeader == null) {
            String message = "No template id header found.";
            item.reject(
                    PipelineItem.Status.FAILED, RejectReason.MALFORMED_TRANSPORT, message);
            log.debug("Validation failed for {}: '{}'", item.getCoordinates(), message);
            return;
        }
        try {
            item.setTemplateType(TemplateType.from(templateIdHeader));
        } catch (IllegalArgumentException e) {
            item.reject(PipelineItem.Status.FAILED, RejectReason.DATA_INCONSISTENCY, e.getMessage(), e);
            log.debug("Validation failed for {}: '{}'", item.getCoordinates(), e.getMessage());
        }
    }
}
