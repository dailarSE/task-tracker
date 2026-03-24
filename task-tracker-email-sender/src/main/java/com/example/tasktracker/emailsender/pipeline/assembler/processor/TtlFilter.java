package com.example.tasktracker.emailsender.pipeline.assembler.processor;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtlFilter implements ItemProcessor {
    private final Clock clock;

    @Override
    public void process(PipelineItem item) {
        Instant now = clock.instant();
        Instant deadline = item.getDeadline();

        if (now.isAfter(deadline)) {
            String message = String.format("Expired. Deadline: %s, Now: %s", deadline, now);
            item.reject(PipelineItem.Status.SKIPPED, RejectReason.TTL_EXPIRED, message);
            log.debug("Validation failed for {}: '{}'", item.getCoordinates(), message);
        }
    }
}
