package com.example.tasktracker.emailsender;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

public abstract class ContainerizedIntegrationTest {
    static final Network NETWORK = Network.newNetwork();

    @ServiceConnection
    static final RedisContainer REDIS =
            new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("8-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("redis-internal");

    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka-internal")
                    .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));

    static {
        Startables.deepStart(REDIS, KAFKA).join();
    }
}
