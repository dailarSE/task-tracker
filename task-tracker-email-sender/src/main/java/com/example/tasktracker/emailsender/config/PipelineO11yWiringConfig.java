package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.infra.RuntimeInstanceIdProvider;
import com.example.tasktracker.emailsender.o11y.observation.context.KafkaContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.RedisContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.SmtpContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.convention.*;
import com.example.tasktracker.emailsender.o11y.observation.util.TelemetryTracker;
import com.example.tasktracker.emailsender.o11y.pipeline.*;
import com.example.tasktracker.emailsender.pipeline.assembler.BatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.ValidatingBatchAssembler;
import com.example.tasktracker.emailsender.pipeline.assembler.processor.*;
import com.example.tasktracker.emailsender.pipeline.idempotency.*;
import com.example.tasktracker.emailsender.pipeline.ratelimit.Bucket4jRpsLimiter;
import com.example.tasktracker.emailsender.pipeline.ratelimit.RpsLimiter;
import com.example.tasktracker.emailsender.pipeline.sender.*;
import io.github.bucket4j.BlockingBucket;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

@Configuration
public class PipelineO11yWiringConfig {
    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "true", matchIfMissing = true)
    public EmailTransport observedEmailTransport(
            JavaMailSender javaMailSender,
            EmailErrorResolver emailErrorResolver,
            ObservationRegistry registry,
            EmailSmtpConvention smtpConvention,
            SmtpContextFactory smtpContextFactory,
            EmailSenderProperties emailSenderProperties
    ) {
        EmailTransport delegate = smtpEmailTransport(javaMailSender, emailSenderProperties, emailErrorResolver);
        return new ObservedEmailTransport(
                delegate,
                registry,
                smtpConvention,
                smtpContextFactory,
                emailSenderProperties
        );
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "false")
    public EmailTransport rawEmailTransport(JavaMailSender javaMailSender,
                                            EmailSenderProperties emailSenderProperties,
                                            EmailErrorResolver emailErrorResolver) {
        return smtpEmailTransport(javaMailSender, emailSenderProperties, emailErrorResolver);
    }

    public SmtpEmailTransport smtpEmailTransport(JavaMailSender javaMailSender,
                                             EmailSenderProperties emailSenderProperties,
                                             EmailErrorResolver emailErrorResolver) {
        return new SmtpEmailTransport(javaMailSender, emailSenderProperties, emailErrorResolver);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "true", matchIfMissing = true)
    public Sender observedSender(
            EmailClient emailClient,
            ObservationRegistry registry,
            KafkaContextFactory kafkaFactory,
            KafkaProcessConvention processConvention,
            TelemetryTracker tracker) {
        return new ObservedSender(
                asyncSender(emailClient),
                registry,
                kafkaFactory,
                processConvention,
                tracker);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "false")
    public Sender rawSender(EmailClient emailClient) {
        return asyncSender(emailClient);
    }

    public AsyncSender asyncSender(EmailClient emailClient) {
        return new AsyncSender(emailClient);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "true", matchIfMissing = true)
    public RpsLimiter observedRpsLimiter(
            ObservationRegistry registry,
            ChunkRateLimitConvention rateLimitConvention,
            BlockingBucket rpsBucket,
            ReliabilityProperties properties) {
        return new ObservedBucket4jRpsLimiter(bucket4jRpsLimiter(rpsBucket, properties), registry, rateLimitConvention);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "false")
    public RpsLimiter rawRpsLimiter(BlockingBucket rpsBucket, ReliabilityProperties properties) {
        return bucket4jRpsLimiter(rpsBucket, properties);
    }

    public Bucket4jRpsLimiter bucket4jRpsLimiter(BlockingBucket rpsBucket, ReliabilityProperties properties) {
        return new Bucket4jRpsLimiter(rpsBucket, properties);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "true", matchIfMissing = true)
    public IdempotencyGuard observedIdempotencyGuard(
            RedisContextFactory contextFactory,
            ObservationRegistry registry,
            @Qualifier("batchIdempotencyScript") RedisScript<List<String>> idempotencyScript,
            RedisConvention redisConvention,
            ReliabilityProperties properties,
            StringRedisTemplate redisTemplate,
            TemplateKeyRegistry keyRegistry,
            RuntimeInstanceIdProvider runtimeInstanceIdProvider
    ) {
        IdempotencyGuard delegate = redisIdempotencyGuard(
                properties, redisTemplate, keyRegistry, runtimeInstanceIdProvider, idempotencyScript
        );
        return new ObservedIdempotencyGuard(delegate, contextFactory, registry, idempotencyScript, redisConvention);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "false")
    public IdempotencyGuard idempotencyGuard(
            ReliabilityProperties properties,
            StringRedisTemplate redisTemplate,
            TemplateKeyRegistry keyRegistry,
            RuntimeInstanceIdProvider runtimeInstanceIdProvider,
            @Qualifier("batchIdempotencyScript") RedisScript<List<String>> idempotencyScript) {
        return redisIdempotencyGuard(properties, redisTemplate, keyRegistry, runtimeInstanceIdProvider, idempotencyScript);
    }

    public RedisIdempotencyGuard redisIdempotencyGuard(ReliabilityProperties properties,
                                                       StringRedisTemplate redisTemplate,
                                                       TemplateKeyRegistry keyRegistry,
                                                       RuntimeInstanceIdProvider runtimeInstanceIdProvider,
                                                       RedisScript<List<String>> idempotencyScript) {
        return new RedisIdempotencyGuard(properties, redisTemplate, keyRegistry, runtimeInstanceIdProvider, idempotencyScript);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "true", matchIfMissing = true)
    public IdempotencyCommitter observedIdempotencyCommitter(
            RedisContextFactory contextFactory,
            ObservationRegistry registry,
            RedisConvention redisConvention,
            StringRedisTemplate redisTemplate,
            TemplateKeyRegistry keyRegistry,
            ReliabilityProperties properties
    ) {
        RedisIdempotencyCommitter delegate = redisIdempotencyCommitter(redisTemplate, keyRegistry, properties);
        return new ObservedIdempotencyCommitter(delegate, contextFactory, registry, redisConvention);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "false")
    public IdempotencyCommitter idempotencyCommitter(
            StringRedisTemplate redisTemplate, TemplateKeyRegistry keyRegistry, ReliabilityProperties properties) {
        return redisIdempotencyCommitter(redisTemplate, keyRegistry, properties);
    }

    public RedisIdempotencyCommitter redisIdempotencyCommitter(
            StringRedisTemplate redisTemplate, TemplateKeyRegistry keyRegistry, ReliabilityProperties properties) {
        return new RedisIdempotencyCommitter(redisTemplate, keyRegistry, properties);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "true", matchIfMissing = true)
    public BatchAssembler observedEmailBatchAssembler(
            ObservationRegistry observationRegistry,
            AssemblyConvention convention,
            MetadataResolver metadataResolver,
            CorrelationIdFilter correlationIdFilter,
            TemplateTypeProcessor typeProcessor,
            TtlFormatProcessor ttlFormatProcessor,
            TtlFilter ttlFilter,
            JsonParser jsonParser,
            Jsr303Filter jsr303Filter,
            ConsistencyFilter consistencyFilter
    ) {
        BatchAssembler delegate = validatingEmailBatchAssembler(
                metadataResolver,
                correlationIdFilter,
                typeProcessor,
                ttlFormatProcessor,
                ttlFilter,
                jsonParser,
                jsr303Filter,
                consistencyFilter
        );
        return new ObservedValidatingEmailBatchAssembler(delegate, observationRegistry, convention);
    }

    @Bean
    @ConditionalOnProperty(value = "app.observation.enabled", havingValue = "false")
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
        return validatingEmailBatchAssembler(
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

    public ValidatingBatchAssembler validatingEmailBatchAssembler(
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
