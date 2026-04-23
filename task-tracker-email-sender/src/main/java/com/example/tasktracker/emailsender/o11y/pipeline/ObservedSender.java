package com.example.tasktracker.emailsender.o11y.pipeline;

import com.example.tasktracker.emailsender.o11y.observation.context.KafkaContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordProcessContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaProcessConvention;
import com.example.tasktracker.emailsender.o11y.observation.util.TelemetryTracker;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.sender.Sender;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class ObservedSender implements Sender {
    private final Sender delegate;
    private final ObservationRegistry registry;
    private final KafkaContextFactory recordContextFactory;
    private final KafkaProcessConvention processConvention;
    private final TelemetryTracker tracker;

    @Override
    public CompletableFuture<Void> sendAsync(PipelineItem item) {

        KafkaRecordProcessContext processContext = recordContextFactory.createProcessContext(item);

        Observation processObservation = Observation.createNotStarted(
                processConvention, () -> processContext, registry);

        return tracker.trackAsync(
                processObservation,
                item,
                () -> delegate.sendAsync(item)
        );
    }
}
