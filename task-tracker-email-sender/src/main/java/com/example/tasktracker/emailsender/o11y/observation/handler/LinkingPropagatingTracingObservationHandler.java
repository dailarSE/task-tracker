package com.example.tasktracker.emailsender.o11y.observation.handler;

import com.example.tasktracker.emailsender.o11y.observation.context.DetachedContext;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaConnectionContext;
import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

/**
 * Хендлер, который умеет добавлять Links при создании Span.
 */
@Slf4j
public class LinkingPropagatingTracingObservationHandler extends DefaultTracingObservationHandler {

    private final Propagator propagator;

    public LinkingPropagatingTracingObservationHandler(Tracer tracer, Propagator propagator) {
        super(tracer);
        this.propagator = propagator;
    }

    @Override
    public void onStart(Observation.Context context) {
        Span.Builder builder;

        if (context instanceof ReceiverContext<?> rc) {
            if (context instanceof DetachedContext) {
                builder = getTracer().spanBuilder();
            } else {
                builder = extract(rc);
            }
        } else {
            builder = getTracer().spanBuilder();
            Span localParent = getParentSpan(context);
            if (localParent != null) {
                builder.setParent(localParent.context());
            }
        }

        if (context instanceof DetachedContext dc && dc.getRemoteParentLink() != null) {
            builder.addLink(new Link(dc.getRemoteParentLink()));
        }

        if (context instanceof ReceiverContext<?> rc) {
            builder.kind(Span.Kind.valueOf(rc.getKind().name()));
            builder.remoteServiceName(rc.getRemoteServiceName());
            setRemoteCoords(rc.getRemoteServiceAddress(), builder);
        } else if (context instanceof SenderContext<?> sc) {
            builder.kind(Span.Kind.valueOf(sc.getKind().name()));
            builder.remoteServiceName(sc.getRemoteServiceName());
            setRemoteCoords(sc.getRemoteServiceAddress(), builder);
        }

        if (context instanceof KafkaConnectionContext kcc && kcc.getServerAddress() != null) {
            builder.remoteIpAndPort(kcc.getServerAddress(), kcc.getServerPort());
        }

        Span span = builder.start();
        getTracingContext(context).setSpan(span);

        if (context instanceof SenderContext<?> sc && sc.getCarrier() != null) {
            inject(span, sc);
        }
    }

    @Override
    public String getSpanName(Observation.Context context) {
        if (StringUtils.isNotBlank(context.getContextualName())) {
            return context.getContextualName();
        }
        return context.getName();
    }

    void setRemoteCoords(String address, Span.Builder builder) {
        if (address != null) {
            try {
                URI uri = URI.create(address);
                builder.remoteIpAndPort(uri.getHost(), uri.getPort());
            } catch (Exception ex) {
                log.warn("Exception [{}], occurred while trying to parse the uri [{}] to host and port.", ex,
                        address);
            }
        }
    }

    private <C> Span.Builder extract(ReceiverContext<C> rc) {
        C carrier = rc.getCarrier();
        var getter = rc.getGetter();
        return this.propagator.extract(carrier, getter::get);
    }

    private <C> void inject(Span span, SenderContext<C> sc) {
        C carrier = sc.getCarrier();
        var setter = sc.getSetter();
        this.propagator.inject(span.context(), carrier, setter::set);
    }
}
