package com.example.tasktracker.emailsender.pipeline;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;

import java.util.List;

public interface ChunkingExecutor {
    void execute(List<PipelineItem> items);
}