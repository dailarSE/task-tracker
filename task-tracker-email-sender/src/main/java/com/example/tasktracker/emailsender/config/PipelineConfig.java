package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.infra.RuntimeInstanceIdProvider;
import com.example.tasktracker.emailsender.pipeline.assembler.BatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.ValidatingBatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.processor.*;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyGuard;
import com.example.tasktracker.emailsender.pipeline.idempotency.TemplateKeyRegistry;
import com.example.tasktracker.emailsender.pipeline.idempotency.RedisIdempotencyCommitter;
import com.example.tasktracker.emailsender.pipeline.idempotency.RedisIdempotencyGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

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

    @Bean
    public IdempotencyGuard idempotencyGuard(
            EmailSenderProperties properties,
            StringRedisTemplate redisTemplate,
            TemplateKeyRegistry keyRegistry,
            RuntimeInstanceIdProvider runtimeInstanceIdProvider,
            @Qualifier("batchIdempotencyScript") RedisScript<List<String>> idempotencyScript) {
        return new RedisIdempotencyGuard(properties, redisTemplate, keyRegistry, runtimeInstanceIdProvider, idempotencyScript);
    }

    @Bean
    public RedisIdempotencyCommitter redisIdempotencyCommitter(
            StringRedisTemplate redisTemplate, TemplateKeyRegistry keyRegistry, EmailSenderProperties properties) {
        return new RedisIdempotencyCommitter(redisTemplate, keyRegistry, properties);
    }
}
