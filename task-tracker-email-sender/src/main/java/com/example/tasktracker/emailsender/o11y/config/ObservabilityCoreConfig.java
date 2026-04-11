package com.example.tasktracker.emailsender.o11y.config;

import com.example.tasktracker.emailsender.o11y.observation.handler.LinkingPropagatingTracingObservationHandler;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(value = "app.observability.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ObservabilityCoreConfig {

    @Bean
    public ContextSnapshotFactory contextSnapshotFactory() {
        return ContextSnapshotFactory.builder()
                .contextRegistry(ContextRegistry.getInstance())
                .clearMissing(true)
                .build();
    }

    @Bean
    public VirtualThreadMetrics virtualThreadMetrics(MeterRegistry registry) {
        VirtualThreadMetrics virtualThreadMetrics = new VirtualThreadMetrics();
        virtualThreadMetrics.bindTo(registry);
        return virtualThreadMetrics;
    }

    @Bean
    public InitializingBean otelLoggingInitializer(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkJvmAccess() {
        Module javaBase = Object.class.getModule();
        Module appModule = this.getClass().getModule();

        boolean isOpen = javaBase.isOpen("java.util.concurrent", appModule);

        if (!isOpen) {
            log.warn("O11Y: JVM access to 'java.util.concurrent' is CLOSED. Virtual Thread metrics will be limited. " +
                    "Consider adding '--add-opens java.base/java.util.concurrent=ALL-UNNAMED' to JVM arguments.");
        }
    }

    @Bean
    @Order(MicrometerTracingAutoConfiguration.DEFAULT_TRACING_OBSERVATION_HANDLER_ORDER)
    public DefaultTracingObservationHandler defaultTracingObservationHandler(Tracer tracer, Propagator propagator) {
        return new LinkingPropagatingTracingObservationHandler(tracer, propagator);
    }

    @Bean
    public PropagatingSenderTracingObservationHandler<?> disabledSenderHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingSenderTracingObservationHandler<>(tracer, propagator) {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return false;
            }
        };
    }

    @Bean
    public PropagatingReceiverTracingObservationHandler<?> disabledReceiverHandler(Tracer tracer, Propagator propagator) {
        return new PropagatingReceiverTracingObservationHandler<>(tracer, propagator) {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return false;
            }
        };
    }
}
