package com.example.tasktracker.emailsender.messaging;

import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.pipeline.EmailProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailBatchListener {
    private final EmailProcessor processor;

    @KafkaListener(
            topics = "${app.email.kafka-topic}",
            containerFactory = "rawBatchFactory",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onBatch(List<ConsumerRecord<byte[], byte[]>> rawRecords) {
        try {
            processor.processBatch(rawRecords);
        } catch (RetryableProcessingException e) {
            throw new BatchListenerFailedException("retry caught", e, 0);
        } catch (Throwable e) {
            log.error("Unhandled batch processing error", e);
            throw e;
        }
    }
}
