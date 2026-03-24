package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;

public interface ItemProcessor {
    void process(PipelineItem item);
}
