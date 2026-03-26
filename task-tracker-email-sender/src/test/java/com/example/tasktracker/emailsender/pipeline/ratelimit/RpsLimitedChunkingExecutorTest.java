package com.example.tasktracker.emailsender.pipeline.ratelimit;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.sender.Sender;
import com.example.tasktracker.emailsender.util.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.example.tasktracker.emailsender.util.TestKafkaConsumerRecordFactory.createItems;
import static org.junit.jupiter.api.Assertions.*;

class RpsLimitedChunkingExecutorTest {

    private static final int CHUNK_SIZE = 20;
    private static final int TOTAL_ITEMS = 55;
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Instant NOW = Instant.parse("2025-01-01T10:00:00Z");

    private final EmailSenderProperties.RateLimitProperties props = new EmailSenderProperties.RateLimitProperties();
    private final MutableClock clock = new MutableClock(NOW);

    private final List<PipelineItem> capturedItems = new ArrayList<>();
    private CompletableFuture<Void> senderFuture;

    private Sender sender;

    private RpsLimitedChunkingExecutor executor;


    @BeforeEach
    void setUp() {
        capturedItems.clear();
        senderFuture = CompletableFuture.completedFuture(null);

        props.setTokenChunkSize(CHUNK_SIZE);
        props.setBatchProcessingTimeout(TIMEOUT);

        sender = new Sender() {
            @Override public CompletableFuture<Void> sendAsync(PipelineItem item) { return null; }

            @Override
            public CompletableFuture<Void> sendChunkAsync(List<PipelineItem> chunk) {
                capturedItems.addAll(chunk);
                return senderFuture;
            }
        };
    }

    @Test
    @DisplayName("Success: All items processed when futures complete instantly")
    void shouldProcessAllItems() throws Exception {
        // Given
        var items = createItems(TOTAL_ITEMS);
        RpsLimiter limiter = (req) -> Math.min(req, CHUNK_SIZE);
        executor = new RpsLimitedChunkingExecutor(limiter, sender, props, clock);

        // When
        executor.execute(items);

        // Then
        assertEquals(TOTAL_ITEMS, capturedItems.size(), "All items must be captured by sender");
    }

    @Test
    @DisplayName("Should abort and cancel futures without waiting when clock advances")
    void shouldAbortOnDeadline() {
        // Given
        var items = createItems(TOTAL_ITEMS);
        senderFuture = new CompletableFuture<>();
        // дедлайн при первом же вызове
        RpsLimiter timeWarpLimiter = (req) -> {
            clock.fastForward(TIMEOUT.plusMillis(1));
            return Math.min(req, CHUNK_SIZE);
        };

        executor = new RpsLimitedChunkingExecutor(timeWarpLimiter, sender, props, clock);

        // Act & Assert
        assertThrows(InfrastructureSuspendedException.class, () -> executor.execute(items));
        assertTrue(senderFuture.isCancelled(), "Active tasks must be cancelled on timeout");
        assertEquals(CHUNK_SIZE, capturedItems.size(), "Only the first chunk should be processed before timeout");
    }
}