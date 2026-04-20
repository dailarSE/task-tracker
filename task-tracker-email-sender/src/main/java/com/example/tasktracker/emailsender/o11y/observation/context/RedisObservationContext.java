package com.example.tasktracker.emailsender.o11y.observation.context;

import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.SenderContext;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
public class RedisObservationContext extends SenderContext<Object> {
    private final String operationName;
    private final String logicalOperationName;
    private final String databaseIndex;
    private final String serverAddress;
    private final int port;
    @Setter private Integer batchSize;
    @Setter private String scriptSha1;

    public RedisObservationContext(String operationName, String logicalOperationName, String address, int port, String dbIndex) {
        super((carrier, key, value) -> {}, Kind.CLIENT);
        this.operationName = Objects.requireNonNull(operationName);
        this.logicalOperationName = Objects.requireNonNull(logicalOperationName);
        this.databaseIndex = Objects.requireNonNull(dbIndex);
        this.serverAddress = Objects.requireNonNull(address);
        this.port = port;
    }
}
