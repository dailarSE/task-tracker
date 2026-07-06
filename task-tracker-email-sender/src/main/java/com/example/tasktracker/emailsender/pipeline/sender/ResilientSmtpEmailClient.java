package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.api.email.EmailHeaders;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.example.tasktracker.emailsender.config.ResilienceConfig.*;
import static com.example.tasktracker.emailsender.util.AsyncUtils.unwrap;

@Component
@Slf4j
public class ResilientSmtpEmailClient implements EmailClient {

    private final EmailTransport transport;
    private final EmailTemplateEngine templateEngine;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService vThreadExecutor;
    private final ExecutorService smtpExecutor;

    public ResilientSmtpEmailClient(EmailTransport transport,
                                    EmailTemplateEngine templateEngine,
                                    @Qualifier(EMAIL_CIRCUIT_BREAKER) CircuitBreaker circuitBreaker,
                                    @Qualifier(EMAIL_BULKHEAD) Bulkhead bulkhead,
                                    @Qualifier(EMAIL_TIME_LIMITER) TimeLimiter timeLimiter,
                                    @Qualifier("resilienceScheduler") ScheduledExecutorService scheduledExecutor,
                                    @Qualifier("virtualThreadExecutor") ExecutorService vThreadExecutor,
                                    @Qualifier("smtpExecutor") ExecutorService smtpExecutor) {
        this.transport = transport;
        this.templateEngine = templateEngine;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.timeLimiter = timeLimiter;
        this.scheduledExecutor = scheduledExecutor;
        this.vThreadExecutor = vThreadExecutor;
        this.smtpExecutor = smtpExecutor;
    }

    public CompletableFuture<Void> send(@NonNull TriggerCommand sendCommand) {

        return CompletableFuture.runAsync(() -> {
                    bulkhead.acquirePermission();

                    try {
                        SendInstructions sendInstructions = buildSendInstructions(sendCommand);

                        Supplier<CompletionStage<Void>> physicalSendTask = () ->
                                CompletableFuture.runAsync(() -> transport.send(sendInstructions), smtpExecutor);

                        Decorators.ofCompletionStage(physicalSendTask)
                                .withTimeLimiter(timeLimiter, scheduledExecutor)
                                .withCircuitBreaker(circuitBreaker)
                                .get()
                                .toCompletableFuture()
                                .join();

                    } finally {
                        bulkhead.onComplete();
                    }

                }, vThreadExecutor)
                .handle(this::translateException);
    }

    private @NonNull SendInstructions buildSendInstructions(@NonNull TriggerCommand sendCommand) {
        var renderingResult = templateEngine.process(
                sendCommand.templateId(),
                sendCommand.templateContext(),
                sendCommand.localeTag()
        );

        HashMap<String, String> headers = new HashMap<>();
        headers.put(EmailHeaders.X_CORRELATION_ID, sendCommand.correlationId());
        headers.put(EmailHeaders.X_TEMPLATE_ID, sendCommand.templateId());

        return new SendInstructions(
                sendCommand.recipientEmail(),
                renderingResult.subject(),
                renderingResult.body(),
                true,
                headers
        );
    }

    /**
     * Стерилизует цепочку исключений, превращая асинхронный контекст в доменные типы.
     */
    Void translateException(Void result, Throwable ex) {
        if (ex == null) return result;

        if (Thread.currentThread().isInterrupted()) {
            throw new InfrastructureSuspendedException("Execution was interrupted", unwrap(ex));
        }

        Throwable core = unwrap(ex);

        throw switch (core) {
            case RetryableProcessingException e -> e;
            case FatalProcessingException e -> e;

            case InterruptedException e -> {
                Thread.currentThread().interrupt();
                yield new InfrastructureSuspendedException("Execution interrupted during send", e);
            }
            case CancellationException e -> new InfrastructureSuspendedException("Task was cancelled", e);

            case Exception e when
                    e instanceof TimeoutException || e.getClass().getPackageName().startsWith("io.github.resilience4j") ->
                    new RetryableProcessingException(RejectReason.INFRASTRUCTURE, "Resilience safety trigger", e);

            case RuntimeException e ->
                    new FatalProcessingException(RejectReason.INTERNAL_ERROR, "Unexpected processing fault", e);

            default -> new FatalProcessingException(RejectReason.INTERNAL_ERROR, "Critical system failure", core);
        };
    }
}