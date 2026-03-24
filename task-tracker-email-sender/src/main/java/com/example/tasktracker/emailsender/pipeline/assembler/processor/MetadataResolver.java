package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.messaging.util.KafkaHeaderReader;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import static com.example.tasktracker.emailsender.api.messaging.MessagingHeaders.*;

@Component
@RequiredArgsConstructor
public class MetadataResolver implements ItemProcessor {
    @Override
    public void process(PipelineItem item) {
        ConsumerRecord<byte[], byte[]> record = item.getOriginalRecord();
        item.setTemplateIdHeader(KafkaHeaderReader.readString(record, X_TEMPLATE_ID).orElse(null));
        item.setValidUntilHeader(KafkaHeaderReader.readString(record, X_VALID_UNTIL).orElse(null));
        item.setCorrelationIdHeader(KafkaHeaderReader.readString(record, X_CORRELATION_ID).orElse(null));
    }
}
