package com.example.tasktracker.emailsender.o11y.observation.util;

import lombok.Getter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
public class RedisPropertiesResolver {
    private final String address;
    private final int port;
    private final String databaseIndex;

    public RedisPropertiesResolver(
            RedisProperties properties,
            ObjectProvider<RedisConnectionDetails> connectionDetailsProvider) {

        RedisConnectionDetails details = connectionDetailsProvider.getIfAvailable();

        if (details != null && details.getStandalone() != null) {
            var standalone = details.getStandalone();
            this.address = standalone.getHost();
            this.port = standalone.getPort();
            this.databaseIndex = String.valueOf(standalone.getDatabase());
        } else {
            this.address = properties.getHost() != null ? properties.getHost() : "localhost";
            this.port = properties.getPort() != 0 ? properties.getPort() : 6379;
            this.databaseIndex = String.valueOf(properties.getDatabase());
        }
    }
}
