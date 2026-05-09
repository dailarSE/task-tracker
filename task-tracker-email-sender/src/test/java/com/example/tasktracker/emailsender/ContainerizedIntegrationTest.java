package com.example.tasktracker.emailsender;

import com.example.tasktracker.emailsender.util.*;
import com.redis.testcontainers.RedisContainer;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

@Import({TestSupportConfig.class,
        ContainerizedIntegrationTest.RedisProxyConfig.class,
        ContainerizedIntegrationTest.ContextGuardConfig.class
})
@ActiveProfiles("ci")
public abstract class ContainerizedIntegrationTest {
    protected static final int SMTP_PROXIED_PORT = 8666;
    protected static final int REDIS_PROXIED_PORT = 8667;
    protected static final Network NETWORK = Network.newNetwork();

    @Autowired
    protected KafkaSupport kafka;
    @Autowired
    protected RedisSupport redis;
    @Autowired
    protected EmailSupport email;
    @Autowired
    protected RpsSupport rps;

    @ServiceConnection
    protected static final RedisContainer REDIS =
            new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("8-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("redis-internal");

    @ServiceConnection
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka-internal")
                    .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));

    protected static final GenericContainer<?> MAILHOG = new GenericContainer<>("mailhog/mailhog:v1.0.1")
            .withExposedPorts(1025, 8025)
            .withNetwork(NETWORK)
            .withNetworkAliases("mailhog");

    protected static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer("shopify/toxiproxy:2.1.0")
            .withNetwork(NETWORK);

    protected static Proxy mailProxy;
    protected static Proxy redisProxy;

    static {
        Startables.deepStart(REDIS, KAFKA, MAILHOG, TOXIPROXY).join();

        ToxiproxyClient toxiproxyClient = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());

        String smtpInternalCoords = MAILHOG.getNetworkAliases().getFirst() + ":" + 1025;
        String redisInternalCoords = REDIS.getNetworkAliases().getFirst() + ":" + 6379;
        try {
            mailProxy = toxiproxyClient.createProxy(
                    "smtp-proxy",
                    "0.0.0.0:" + SMTP_PROXIED_PORT,
                    smtpInternalCoords);
            redisProxy = toxiproxyClient.createProxy(
                    "redis-proxy",
                    "0.0.0.0:" + REDIS_PROXIED_PORT,
                    redisInternalCoords);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @TestConfiguration
    static class ContextGuardConfig {
        @Bean
        public Object contextGuard() {
            SingleContextGuard.verify();
            return new Object();
        }
    }

    @TestConfiguration
    static class RedisProxyConfig {
        @Bean
        @Primary
        public RedisConnectionDetails redisConnectionDetails() {
            return new RedisConnectionDetails() {
                @Override
                public Standalone getStandalone() {
                    return new Standalone() {
                        @Override
                        public String getHost() {
                            return TOXIPROXY.getHost();
                        }

                        @Override
                        public int getPort() {
                            return TOXIPROXY.getMappedPort(REDIS_PROXIED_PORT);
                        }
                    };
                }
            };
        }
    }

    @DynamicPropertySource
    protected static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.email.reliability.kafka-retry.blocking-retry-interval", () -> "200ms");
        registry.add("spring.mail.host", TOXIPROXY::getHost);
        registry.add("spring.mail.port", () -> TOXIPROXY.getMappedPort(SMTP_PROXIED_PORT));
        registry.add("test.mailhog.url",
                () -> String.format("http://%s:%d", MAILHOG.getHost(), MAILHOG.getMappedPort(8025)));
    }

    @AfterEach
    protected void tearDown() {
        resetToxics();
        resetRedis();
        resetMailHog();
        resetKafkaListener();
    }

    private void resetToxics() {
        resetMailToxics();
        resetRedisToxics();
    }

    @SneakyThrows
    private void resetRedisToxics() {
        for (Toxic toxic : redisProxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    @SneakyThrows
    private void resetMailToxics() {
        for (Toxic toxic : mailProxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    private void resetMailHog() {
        email.clear();
    }

    private void resetRedis() {
        redis.clear();
    }

    private void resetKafkaListener() {
        kafka.clear();
    }
}
