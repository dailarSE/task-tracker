package com.example.tasktracker.emailsender.pipeline;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.pipeline.assembler.BatchAssembler;
import com.example.tasktracker.emailsender.pipeline.dispatch.DispatcherStep;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyCommitter;
import com.example.tasktracker.emailsender.pipeline.idempotency.IdempotencyGuard;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class EmailProcessor {

    private final BatchAssembler assembler;
    private final IdempotencyGuard idempotencyGuard;
    private final ChunkingExecutor chunkingExecutor;
    private final DispatcherStep dispatcherStep;
    private final IdempotencyCommitter committer;

    public PipelineBatch processBatch(List<ConsumerRecord<byte[], byte[]>> rawRecords) {
        if (rawRecords == null || rawRecords.isEmpty()) return new PipelineBatch(Collections.emptyList());
        log.debug("Starting processing batch of {} records", rawRecords.size());
        return process(rawRecords, dispatcherStep::dispatch);

    }

    public PipelineBatch processSingle(ConsumerRecord<byte[], byte[]> rawRecord) {
        if (rawRecord == null) return new PipelineBatch(Collections.emptyList());
        log.info("Starting single-item processing: {}", formatCoordinates(rawRecord));
        return process(List.of(rawRecord), batch ->
                batch.items()
                        .getFirst()
                        .getStage()
                        .toException()
                        .ifPresent(ex -> {
                            throw ex;
                        })
        );
    }

    PipelineBatch process(List<ConsumerRecord<byte[], byte[]>> rawRecords, Consumer<PipelineBatch> finisher)
            throws FatalProcessingException, RetryableProcessingException {
        PipelineBatch batch = assembler.assemble(rawRecords);

        try {
            idempotencyGuard.checkAndLock(batch);

            chunkingExecutor.execute(batch.getPendingItems());

            committer.commitSuccess(batch);

            finisher.accept(batch);

            return batch;
        } finally {
            logOutcome(batch);
        }
    }

    private void logOutcome(PipelineBatch batch) {
        var items = batch.items();
        if (items == null || items.isEmpty()) return;

        int total = items.size();

        if (total == 1) {
            if (log.isDebugEnabled()) {
                var stage = items.getFirst().getStage();
                log.debug("Single item coordinate: {} | outcome: {}/{}",
                        items.getFirst().getCoordinates(), stage.status(), stage.rejectReason());
            }
            return;
        }

        int failed = 0, retry = 0, sent = 0, skipped = 0;

        for (var item : items) {
            switch (item.getStage().status()) {
                case SENT    -> sent++;
                case SKIPPED -> skipped++;
                case FAILED  -> failed++;
                case RETRY   -> retry++;
                default      -> {}
            }
        }

        if (failed + retry > 0) {
            log.warn("Batch processed with issues. Total: {} | Issues: [FAILED: {}, RETRY: {}] | Success: [SENT: {}, SKIPPED: {}]",
                    total, failed, retry, sent, skipped);
        } else {
            log.info("Batch processed successfully. Total: {} | [SENT: {}, SKIPPED: {}]",
                    total, sent, skipped);
        }
    }

    private String formatCoordinates(ConsumerRecord<byte[], byte[]> r) {
        return r.topic() + "-" + r.partition() + "@" + r.offset();
    }
}