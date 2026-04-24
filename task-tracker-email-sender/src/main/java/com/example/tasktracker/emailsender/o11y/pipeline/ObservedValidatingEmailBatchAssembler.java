package com.example.tasktracker.emailsender.o11y.pipeline;

import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.o11y.observation.context.AssemblyContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.AssemblyConvention;
import com.example.tasktracker.emailsender.pipeline.assembler.BatchAssembler;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.List;

@RequiredArgsConstructor
public class ObservedValidatingEmailBatchAssembler implements BatchAssembler {

    private final BatchAssembler delegate;
    private final ObservationRegistry registry;
    private final AssemblyConvention convention;

    @Override
    public PipelineBatch assemble(List<ConsumerRecord<byte[], byte[]>> records) throws InfrastructureException {
        AssemblyContext context = new AssemblyContext();

        return Observation.createNotStarted(convention, () -> context, registry)
                .observe(() -> delegate.assemble(records));
    }
}
