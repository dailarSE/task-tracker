package com.example.tasktracker.emailsender.messaging;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.pipeline.EmailProcessor;
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

    @KafkaListener(
            topics = "${app.email.retry-topic}",
            groupId = "${spring.kafka.consumer.group-id}-retry",
            containerFactory = "rawSingleRetryFactory"
    )
    public void onRetry(ConsumerRecord<byte[], byte[]> record) {
        try {
            emailProcessor.processSingle(record);
        } catch (RetryableProcessingException | FatalProcessingException e) {
            throw e;
        } catch (Throwable e) {
            log.error("Unhandled retry processing error", e);
            throw e;
        }
    }
}
