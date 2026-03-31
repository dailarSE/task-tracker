package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.infra.RuntimeInstanceIdProvider;
import com.example.tasktracker.emailsender.pipeline.ChunkingExecutor;
import com.example.tasktracker.emailsender.pipeline.assembler.BatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.ValidatingBatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.processor.*;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyGuard;
import com.example.tasktracker.emailsender.pipeline.idempotency.RedisIdempotencyCommitter;
import com.example.tasktracker.emailsender.pipeline.idempotency.RedisIdempotencyGuard;
import com.example.tasktracker.emailsender.pipeline.idempotency.TemplateKeyRegistry;
import com.example.tasktracker.emailsender.pipeline.ratelimit.Bucket4jRpsLimiter;
import com.example.tasktracker.emailsender.pipeline.ratelimit.RpsLimitedChunkingExecutor;
import com.example.tasktracker.emailsender.pipeline.ratelimit.RpsLimiter;
import com.example.tasktracker.emailsender.pipeline.sender.AsyncSender;
import com.example.tasktracker.emailsender.pipeline.sender.EmailClient;
import com.example.tasktracker.emailsender.pipeline.sender.Sender;
import io.github.bucket4j.BlockingBucket;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
            ReliabilityProperties properties,
            StringRedisTemplate redisTemplate,
            TemplateKeyRegistry keyRegistry,
            RuntimeInstanceIdProvider runtimeInstanceIdProvider,
            @Qualifier("batchIdempotencyScript") RedisScript<List<String>> idempotencyScript) {
        return new RedisIdempotencyGuard(properties, redisTemplate, keyRegistry, runtimeInstanceIdProvider, idempotencyScript);
    }

    @Bean
    public RedisIdempotencyCommitter redisIdempotencyCommitter(
            StringRedisTemplate redisTemplate, TemplateKeyRegistry keyRegistry, ReliabilityProperties properties) {
        return new RedisIdempotencyCommitter(redisTemplate, keyRegistry, properties);
    }

    @Bean
    public RpsLimiter rpsLimiter(BlockingBucket rpsBucket, ReliabilityProperties properties) {
        return new Bucket4jRpsLimiter(rpsBucket, properties);
    }

    @Bean
    public AsyncSender asyncSender(EmailClient client) {
        return new AsyncSender(client);
    }

    @Bean
    public ChunkingExecutor chunkingExecutor(RpsLimiter rpsLimiter, Sender asyncSender, ReliabilityProperties properties, Clock clock) {
        return new RpsLimitedChunkingExecutor(rpsLimiter, asyncSender, properties, clock);
    }

    @Bean("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
