package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JsonParser implements ItemProcessor {
    private final ObjectMapper objectMapper;

    @Override
    public void process(PipelineItem item) {
        try {
            TriggerCommand cmd = objectMapper.readValue(item.getOriginalRecord().value(), TriggerCommand.class);
            item.setPayload(cmd);
        } catch (IOException e) {
            item.reject(PipelineItem.Status.FAILED, RejectReason.MALFORMED_JSON, "JSON Syntax Error", e);
        }
    }
}
