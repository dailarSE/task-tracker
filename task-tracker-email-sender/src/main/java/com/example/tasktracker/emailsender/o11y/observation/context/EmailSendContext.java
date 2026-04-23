package com.example.tasktracker.emailsender.o11y.observation.context;

import com.example.tasktracker.emailsender.api.email.EmailHeaders;
import com.example.tasktracker.emailsender.pipeline.sender.SendInstructions;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.SenderContext;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;

@Getter
public class EmailSendContext extends SenderContext<Map<String, String>> {
    public static final String UNKNOWN_VALUE = "unset";

    private final String fromAddress;
    private final String remoteHost;
    private final String remotePort;
    private final String contentType;
    private final String toDomain;

    public EmailSendContext(SendInstructions instructions,
                            String from,
                            String host,
                            int port,
                            String toDomain,
                            boolean isHtml) {
        super((carrier, key, value) -> {
            if (carrier != null) {
                carrier.put(key, value);
            }
        }, Kind.CLIENT);
        this.fromAddress = Objects.requireNonNull(from);
        this.remoteHost = Objects.requireNonNull(host);
        this.remotePort = String.valueOf(port);
        this.toDomain = Objects.requireNonNull(toDomain);
        this.contentType = isHtml ? "text/html" : "text/plain";
        setCarrier(instructions.headers());
    }

    public String getTemplateId() {
        if (getCarrier() != null) {
            return getCarrier().getOrDefault(EmailHeaders.X_TEMPLATE_ID, UNKNOWN_VALUE);
        }
        return UNKNOWN_VALUE;
    }

    public String getCorrelationId() {
        if (getCarrier() != null) {
            return getCarrier().getOrDefault(EmailHeaders.X_CORRELATION_ID, UNKNOWN_VALUE);
        }
        return UNKNOWN_VALUE;
    }
}
