package com.example.tasktracker.emailsender.util;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class SingleContextGuard {
    private static final AtomicInteger contextInitCount = new AtomicInteger(0);

    public static void verify() {
        int count = contextInitCount.incrementAndGet();
        log.debug("Checking Context Invariant. Current count: {}", count);

        if (count > 1) {
            log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            log.error("FATAL: Multiple Spring Contexts detected (count={})!", count);
            log.error("Check for @MockitoSpyBean, @MockBean or different @SpringBootTest configs.");
            log.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

            throw new IllegalStateException(
                    "CRITICAL INVARIANT VIOLATION: Test suite is allowed to have only ONE Spring Context. " +
                            "Detected context initialization #" + count
            );
        }
    }
}
