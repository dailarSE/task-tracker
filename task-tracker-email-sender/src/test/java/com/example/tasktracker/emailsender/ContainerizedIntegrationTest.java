package com.example.tasktracker.emailsender;

import com.example.tasktracker.emailsender.util.EmailSupport;
import com.example.tasktracker.emailsender.util.KafkaSupport;
import com.example.tasktracker.emailsender.util.RedisSupport;
import com.example.tasktracker.emailsender.util.TestSupportConfig;
import com.redis.testcontainers.RedisContainer;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
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

@Import(TestSupportConfig.class)
public abstract class ContainerizedIntegrationTest {
    protected static final int SMTP_PROXIED_PORT = 8666;
    protected static final Network NETWORK = Network.newNetwork();

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected KafkaSupport kafka;
    @Autowired
    protected RedisSupport redis;
    @Autowired
    protected EmailSupport email;

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

    static {
        Startables.deepStart(REDIS, KAFKA, MAILHOG, TOXIPROXY).join();

        ToxiproxyClient toxiproxyClient = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());

        String smtpInternalCoords = MAILHOG.getNetworkAliases().getFirst() + ":" + 1025;
        try {
            mailProxy = toxiproxyClient.createProxy(
                    "smtp",
                    "0.0.0.0:" + SMTP_PROXIED_PORT,
                    smtpInternalCoords);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    protected static void properties(DynamicPropertyRegistry registry) {
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

    private void resetToxics(){
        resetMailToxics();
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

    private void resetKafkaListener(){
        kafka.clear();
    }
}
