package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.AsyncUtils;
import com.example.tasktracker.emailsender.util.DirectExecutorService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ResilientSmtpEmailClientTest {

    private final ExecutorService directExecutor = new DirectExecutorService();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final EmailTemplateEngine engine = (templateName, context, localeTag) ->
            new EmailTemplateEngine.RenderingResult("test-subject", "test-body");
    private final TriggerCommand dummyCommand = new TriggerCommand("test@test.com",
            "TEMPLATE",
            Map.of(),
            "en",
            1L,
            "corr-1");

    private EmailClient client = null;

    EmailClient buildClient(EmailTransport transport) {
        var cb = CircuitBreaker.ofDefaults("test");
        var bulkhead = Bulkhead.ofDefaults("test");
        var timeLimiter = TimeLimiter.ofDefaults("test");

        return new ResilientSmtpEmailClient(transport, engine, cb, bulkhead, timeLimiter, scheduler, directExecutor);
    }

    @AfterEach
    void shutdown() {
        client = null;
        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("emailTransportInfra: Сбой канала связи (InfrastructureException)")
    void shouldReturnInfraException() {
        client = buildClient(inst -> {
            throw new InfrastructureException("SMTP connection dropped", null);
        });

        CompletableFuture<Void> future = client.send(dummyCommand);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
        assertInstanceOf(InfrastructureException.class, AsyncUtils.unwrap(ex));
    }

    @Test
    @DisplayName("emailTransportRetry: Временная ошибка SMTP 4xx (RetryableProcessingException)")
    void shouldReturnRetryableException() {
        client = buildClient(inst -> {
            throw new RetryableProcessingException(RejectReason.REMOTE_ERROR, "421 Service busy", null);
        });

        CompletableFuture<Void> future = client.send(dummyCommand);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
        assertInstanceOf(RetryableProcessingException.class, AsyncUtils.unwrap(ex));
    }

    @Test
    @DisplayName("emailTransportFatal: Фатальная ошибка SMTP 5xx (FatalProcessingException)")
    void shouldReturnFatalException() {
        client = buildClient(inst -> {
            throw new FatalProcessingException(RejectReason.INVALID_PAYLOAD, "550 No such user", null);
        });

        CompletableFuture<Void> future = client.send(dummyCommand);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
        Throwable internal = AsyncUtils.unwrap(ex);
        assertInstanceOf(FatalProcessingException.class, internal);
        assertEquals(RejectReason.INVALID_PAYLOAD, ((FatalProcessingException) internal).getRejectReason());
    }
}