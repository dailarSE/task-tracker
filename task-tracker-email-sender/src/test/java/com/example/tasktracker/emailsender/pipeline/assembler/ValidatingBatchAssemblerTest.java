package com.example.tasktracker.emailsender.pipeline.assembler;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.pipeline.assembler.processor.*;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ValidatingBatchAssemblerTest {
    private static final String VALID_BODY = """
            {
                "recipientEmail": "test@test.com",
                "templateId": "USER_WELCOME",
                "userId": 123,
                "correlationId": "corr-1",
                "templateContext": {}
            }
            """;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Validator validator = mock(Validator.class);
    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneId.of("UTC"));

    private ValidatingBatchAssembler assembler;

    private MetadataResolver metadataResolver;
    private CorrelationIdFilter correlationIdFilter;
    private TemplateTypeProcessor typeProcessor;
    private TtlFormatProcessor ttlFormatProcessor;
    private TtlFilter ttlFilter;
    private JsonParser jsonParser;
    private Jsr303Filter jsr303Filter;
    private ConsistencyFilter consistencyFilter;

    @BeforeEach
    void setUp() {
        reset(validator);
        when(validator.validate(any())).thenReturn(Collections.emptySet());

        EmailSenderProperties props = new EmailSenderProperties();
        props.getMessageValidity().getPolicies().put(TemplateType.USER_WELCOME, Duration.ofHours(1));

        metadataResolver = new MetadataResolver();
        correlationIdFilter = new CorrelationIdFilter();
        typeProcessor = new TemplateTypeProcessor();
        ttlFormatProcessor = new TtlFormatProcessor(props);
        ttlFilter = new TtlFilter(fixedClock);
        jsonParser = new JsonParser(OBJECT_MAPPER);
        jsr303Filter = new Jsr303Filter(validator);
        consistencyFilter = new ConsistencyFilter();

        assembler = new ValidatingBatchAssembler(
                metadataResolver, correlationIdFilter, typeProcessor, ttlFormatProcessor,
                ttlFilter, jsonParser, jsr303Filter, consistencyFilter
        );
    }

    @Test
    @DisplayName("Happy Path: Full valid message should reach PENDING status")
    void shouldAssembleValidRecord() {
        var record = TestKafkaConsumerRecordFactory.record()
                .template("USER_WELCOME")
                .validUntil("2025-01-01T10:30:00Z")
                .correlationId("corr-1")
                .body(VALID_BODY)
                .build();

        PipelineBatch batch = assembler.assemble(List.of(record));

        PipelineItem item = batch.items().getFirst();
        assertEquals(PipelineItem.Status.PENDING, item.getStage().status());
        assertNotNull(item.getPayload());
        assertEquals("corr-1", item.getPayload().correlationId());
    }

    @Test
    @DisplayName("Batch Isolation: One bad record should not affect valid ones")
    void shouldMaintainIsolationInBatch() {
        var validRecord =
                TestKafkaConsumerRecordFactory.record()
                        .template("USER_WELCOME")
                        .validUntil("2025-01-01T10:30:00Z")
                        .correlationId("corr-1")
                        .body(VALID_BODY)
                        .build();
        var invalidRecord =
                TestKafkaConsumerRecordFactory.record().build();// Metadata/Type

        PipelineBatch batch = assembler.assemble(List.of(validRecord, invalidRecord));

        assertEquals(PipelineItem.Status.PENDING, batch.items().get(0).getStage().status());
        assertEquals(PipelineItem.Status.FAILED, batch.items().get(1).getStage().status());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideArchitectureScenarios")
    @DisplayName("Fail-Fast: Early filters should stop processing before expensive steps")
    void shouldEnforceOrderAndFailFast(String description, ConsumerRecord<byte[], byte[]> record, RejectReason expectedReason, boolean payloadShouldBeNull) {
        PipelineBatch batch = assembler.assemble(List.of(record));
        PipelineItem item = batch.items().getFirst();

        assertEquals(expectedReason, item.getStage().rejectReason());
        if (payloadShouldBeNull) {
            assertNull(item.getPayload(), "Payload parsing should have been skipped to save resources");
        } else {
            assertNotNull(item.getPayload(), "Payload was expected to be parsed");
        }
    }

    private static Stream<Arguments> provideArchitectureScenarios() {
        return Stream.of(
                Arguments.of(
                        "Transport over Logic: Missing headers should fail before JSON parsing",
                        TestKafkaConsumerRecordFactory.record()
                                .body("{invalid json")
                                .build(),
                        RejectReason.MALFORMED_TRANSPORT, true
                ),
                Arguments.of(
                        "CPU Protection: Expired TTL should skip JSON parsing",
                        TestKafkaConsumerRecordFactory.record()
                                .template("USER_WELCOME")
                                .timestamp(Instant.parse("2024-01-01T10:00:00Z"))
                                .correlationId("c1")
                                .body("{invalid json}")
                                .build(),
                        RejectReason.TTL_EXPIRED, true
                ),
                Arguments.of(
                        "Syntax over Semantics: Broken JSON syntax should fail before JSR-303 validation",
                        TestKafkaConsumerRecordFactory.record()
                                .template("USER_WELCOME")
                                .timestamp(Instant.parse("2025-01-01T11:00:00Z"))
                                .correlationId("c1")
                                .body("""
                                        "userId": "not-a-number",
                                        """)
                                .build(),
                        RejectReason.MALFORMED_JSON, true
                ),
                Arguments.of(
                        "Consistency check: Mismatch between Header and Body should be the final gate",
                        TestKafkaConsumerRecordFactory.record().
                                template("USER_WELCOME")
                                .timestamp(Instant.parse("2025-01-01T11:00:00Z"))
                                .correlationId("c1")
                                .body("""
                                        {
                                            "recipientEmail": "test@test.com",
                                            "templateId": "DAILY_TASK_REPORT",
                                            "userId": 123,
                                            "correlationId": "corr-1",
                                            "templateContext": {}
                                        }
                                        """)
                                .build(),
                        RejectReason.DATA_INCONSISTENCY, false
                )
        );
    }

    @Test
    @DisplayName("Infrastructure Failure: Exception should propagate to the upstream caller")
    void shouldPropagateInfrastructureException() {
        JsonParser failingParser = new JsonParser(OBJECT_MAPPER) {
            @Override
            public void process(PipelineItem item) {
                throw new InfrastructureException("External System Failure", null);
            }
        };

        ValidatingBatchAssembler infraAssembler = new ValidatingBatchAssembler(
                metadataResolver, correlationIdFilter, typeProcessor, ttlFormatProcessor, ttlFilter, failingParser, jsr303Filter, consistencyFilter
        );

        var record = TestKafkaConsumerRecordFactory.record()
                .template("USER_WELCOME")
                .timestamp(Instant.parse("2025-01-01T11:00:00Z"))
                .correlationId("c1")
                .build();

        assertThrows(InfrastructureException.class, () -> infraAssembler.assemble(List.of(record)));
    }

    @Test
    @DisplayName("Logic: Unexpected RuntimeException should be caught and mark item as INTERNAL_ERROR")
    void shouldHandleUnexpectedRuntimeException() {
        CorrelationIdFilter buggyProcessor = new CorrelationIdFilter() {
            @Override
            public void process(PipelineItem item) {
                throw new RuntimeException("Unexpected bug in code");
            }
        };

        ValidatingBatchAssembler faultyAssembler = new ValidatingBatchAssembler(
                metadataResolver, buggyProcessor, typeProcessor, ttlFormatProcessor,
                ttlFilter, jsonParser, jsr303Filter, consistencyFilter
        );

        var record = TestKafkaConsumerRecordFactory.record()
                .template("USER_WELCOME")
                .correlationId("c1")
                .build();

        PipelineBatch batch = faultyAssembler.assemble(List.of(record));
        PipelineItem item = batch.items().getFirst();

        assertEquals(PipelineItem.Status.FAILED, item.getStage().status());

        assertEquals(RejectReason.INTERNAL_ERROR, item.getStage().rejectReason());

        assertTrue(item.getStage().rejectDescription().contains("Fault in component ["));

        assertInstanceOf(RuntimeException.class, item.getStage().rejectCause());
        assertEquals("Unexpected bug in code", item.getStage().rejectCause().getMessage());
    }

}