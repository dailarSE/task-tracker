package com.example.tasktracker.emailsender.pipeline.assembler;

import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.List;

public interface BatchAssembler {
    /**
     * @throws InfrastructureException если произошел сбой внешних зависимостей, без гарантий определенного результата
     */
    PipelineBatch assemble(List<ConsumerRecord<byte[], byte[]>> records) throws InfrastructureException;
}