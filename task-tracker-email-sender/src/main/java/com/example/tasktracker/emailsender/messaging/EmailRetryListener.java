package com.example.tasktracker.emailsender.messaging;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.o11y.observation.context.KafkaContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordReceiveContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaReceiveConvention;
import com.example.tasktracker.emailsender.pipeline.EmailProcessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryListener {

    private final EmailProcessor emailProcessor;
    private final ObservationRegistry registry;
    private final KafkaReceiveConvention<KafkaRecordReceiveContext> convention;
    private final KafkaContextFactory contextFactory;

    @KafkaListener(
            topics = "${app.email.retry-topic}",
            groupId = "${spring.kafka.consumer.group-id}-retry",
            containerFactory = "rawSingleRetryFactory"
    )
    public void onRetry(ConsumerRecord<byte[], byte[]> record) {
        Observation.createNotStarted(convention, () -> contextFactory.createRecordReceiveContext(record), registry)
                .observe(() -> {
                    try {
                        return emailProcessor.processSingle(record);
                    } catch (RetryableProcessingException | FatalProcessingException e) {
                        throw e;
                    } catch (Throwable e) {
                        log.error("Unhandled retry processing error", e);
                        throw e;
                    }
                });
    }
}
