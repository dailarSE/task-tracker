package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.pipeline.assembler.BatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.ValidatingBatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.processor.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PipelineConfig {
    @Bean
    public BatchAssembler emailBatchAssembler(
            MetadataResolver metadataResolver,
            CorrelationIdFilter correlationIdFilter,
            TemplateTypeProcessor typeProcessor,
            TtlFormatProcessor ttlFormatProcessor,
            TtlFilter ttlFilter,
            JsonParser jsonParser,
            Jsr303Filter jsr303Filter,
            ConsistencyFilter consistencyFilter
    ) {
        return new ValidatingBatchAssembler(
                metadataResolver,
                correlationIdFilter,
                typeProcessor,
                ttlFormatProcessor,
                ttlFilter,
                jsonParser,
                jsr303Filter,
                consistencyFilter
        );
    }
}
