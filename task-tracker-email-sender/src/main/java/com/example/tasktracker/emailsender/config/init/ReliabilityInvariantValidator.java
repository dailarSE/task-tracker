package com.example.tasktracker.emailsender.config.init;

import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReliabilityInvariantValidator {
    private final KafkaProperties kafkaProperties;
    private final ReliabilityProperties reliabilityProperties;

    @PostConstruct
    private void checkKafkaBatchSize() {
        Integer kafkaMaxPoll = kafkaProperties.getConsumer().getMaxPollRecords();
        int resilienceBatchSize = reliabilityProperties.getBudget().getCommandIntakeBatchSize();

        int effectiveKafkaMaxPoll = (kafkaMaxPoll != null) ? kafkaMaxPoll : ConsumerConfig.DEFAULT_MAX_POLL_RECORDS;

        if (effectiveKafkaMaxPoll != resilienceBatchSize) {
            throw new InconsistentConfigurationException(
                    String.format("Kafka max-poll-records (%d) != Reliability batch size (%d)",
                            effectiveKafkaMaxPoll, resilienceBatchSize),
                    "Update 'spring.kafka.consumer.max-poll-records' to match 'app.email.reliability.budget.command-intake-batch-size'."
            );
        }
    }
}
