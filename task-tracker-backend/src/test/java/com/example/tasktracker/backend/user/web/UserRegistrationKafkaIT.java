package com.example.tasktracker.backend.user.web;

import com.example.tasktracker.backend.kafka.domain.entity.UndeliveredWelcomeEmail;
import com.example.tasktracker.backend.kafka.domain.repository.UndeliveredWelcomeEmailRepository;
import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.messaging.dto.EmailTriggerCommand;
import com.example.tasktracker.backend.user.repository.UserRepository;
import com.example.tasktracker.backend.web.ApiConstants;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.toxic.Latency;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ci")
@Slf4j
class UserRegistrationKafkaIT {
    private static final String TEST_TOPIC_NAME = "task_tracker.notifications.email_commands"; // Будет создан через KAFKA_CREATE_TOPICS_EXTRA
    private static String KAFKA_BOOTSTRAP_SERVERS_FOR_APP_VIA_TOXIPROXY; // ToxiProxy's host:mapped_port
    private static final String KAFKA_INTERNAL_NETWORK_ALIAS = "kafka";
    private static final int TOXIPROXY_INTERNAL_KAFKA_LISTEN_PORT = 8666;

    // Внутренние порты Kafka в контейнере,
    private static final int KAFKA_INTERNAL_PORT_INTERNAL_NET = 9092; // Для KAFKA_LISTENERS INTERNAL://:9092
    private static final int KAFKA_INTERNAL_PORT_CONTROLLER = 9093;   // Для KAFKA_LISTENERS CONTROLLER://:9093
    private static final int KAFKA_INTERNAL_PORT_EXTERNAL_APP = 9094; // Для KAFKA_LISTENERS EXTERNAL://:9094 (сюда будет проксировать ToxiProxy)

    // Имена слушателей,
    private static final String LISTENER_NAME_INTERNAL_NET = "INTERNAL";
    private static final String LISTENER_NAME_EXTERNAL_APP = "EXTERNAL"; // Приложение будет использовать этот listener name
    private static final String LISTENER_NAME_CONTROLLER = "CONTROLLER";

    static Network network = Network.newNetwork();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17.4-alpine")
                    .withNetwork(network)
                    .withNetworkAliases("postgres-db");

    @Container
    static final ToxiproxyContainer toxiproxyContainer =
            new ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.12.0"))
                    .withNetwork(network);

    static final GenericContainer<?> kafkaInternalContainer;

    static Proxy kafkaToToxiproxyProxy;

    static {
        kafkaInternalContainer = new GenericContainer<>(DockerImageName.parse("apache/kafka:4.0.0"))
                .withNetwork(network)
                .withNetworkAliases(KAFKA_INTERNAL_NETWORK_ALIAS) // Имя сервиса 'kafka'
                .withExposedPorts(KAFKA_INTERNAL_PORT_EXTERNAL_APP) // Пробрасываем только порт, к которому будет подключаться ToxiProxy
                // Остальные порты (9092, 9093) используются только внутри Docker-сети
                .withEnv("KAFKA_ENABLE_KRAFT", "yes")
                .withEnv("KAFKA_BROKER_ID", "1")
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_LISTENERS", String.join(",",
                        LISTENER_NAME_INTERNAL_NET + "://:" + KAFKA_INTERNAL_PORT_INTERNAL_NET,
                        LISTENER_NAME_CONTROLLER + "://:" + KAFKA_INTERNAL_PORT_CONTROLLER,
                        LISTENER_NAME_EXTERNAL_APP + "://:" + KAFKA_INTERNAL_PORT_EXTERNAL_APP
                ))
                // KAFKA_ADVERTISED_LISTENERS будет установлен в @BeforeAll
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", String.join(",",
                        LISTENER_NAME_INTERNAL_NET + ":PLAINTEXT",
                        LISTENER_NAME_EXTERNAL_APP + ":PLAINTEXT",
                        LISTENER_NAME_CONTROLLER + ":PLAINTEXT"
                ))
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", LISTENER_NAME_INTERNAL_NET)
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", LISTENER_NAME_CONTROLLER)
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@" + KAFKA_INTERNAL_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT_CONTROLLER)

                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_OFFSETS_TOPIC_NUM_PARTITIONS", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                .withEnv("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", Long.MAX_VALUE + "")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes")
                .withEnv("KAFKA_GROUP_MIN_SESSION_TIMEOUT_MS", "1000")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));
    }

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UndeliveredWelcomeEmailRepository undeliveredRepository;

    private String registerUserApiUrl;
    private KafkaConsumer<String, EmailTriggerCommand> testKafkaConsumer;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.topic-verifier.enabled", () -> "true");

        registry.add("spring.kafka.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS_FOR_APP_VIA_TOXIPROXY);

        registry.add("spring.kafka.producer.properties.delivery.timeout.ms", () -> "3000");
        registry.add("spring.kafka.producer.properties.request.timeout.ms", () -> "1000");
        registry.add("spring.kafka.producer.properties.max.block.ms", () -> "2000");
        registry.add("spring.kafka.producer.retries", () -> "1");
    }

    @BeforeAll
    static void beforeAll() throws IOException {
        if (!toxiproxyContainer.isRunning()) {
            toxiproxyContainer.start();
        }

        KAFKA_BOOTSTRAP_SERVERS_FOR_APP_VIA_TOXIPROXY = toxiproxyContainer.getHost() + ":" +
                toxiproxyContainer.getMappedPort(TOXIPROXY_INTERNAL_KAFKA_LISTEN_PORT);
        log.info("Application Kafka Bootstrap Servers (via ToxiProxy for listener {}): {}",
                LISTENER_NAME_EXTERNAL_APP, KAFKA_BOOTSTRAP_SERVERS_FOR_APP_VIA_TOXIPROXY);

        String advertisedListenersValue = String.join(",",
                LISTENER_NAME_INTERNAL_NET + "://" + KAFKA_INTERNAL_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT_INTERNAL_NET,
                LISTENER_NAME_EXTERNAL_APP + "://" + KAFKA_BOOTSTRAP_SERVERS_FOR_APP_VIA_TOXIPROXY, // Вот это важно!
                LISTENER_NAME_CONTROLLER + "://" + KAFKA_INTERNAL_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT_CONTROLLER
        );
        kafkaInternalContainer.addEnv("KAFKA_ADVERTISED_LISTENERS", advertisedListenersValue);

        ToxiproxyClient toxiproxyApiClient = new ToxiproxyClient(toxiproxyContainer.getHost(), toxiproxyContainer.getControlPort());
        try {
            toxiproxyApiClient.getProxy("kafka-proxy").delete();
        } catch (IOException ignored) {
        }

        kafkaToToxiproxyProxy = toxiproxyApiClient.createProxy(
                "kafka-proxy",
                "0.0.0.0:" + TOXIPROXY_INTERNAL_KAFKA_LISTEN_PORT,
                KAFKA_INTERNAL_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_PORT_EXTERNAL_APP // ToxiProxy -> на порт 9094 Kafka
        );

        if (!kafkaInternalContainer.isRunning()) {
            kafkaInternalContainer.start();
        }

        setUpKafkaTopic();
    }

    static void setUpKafkaTopic() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS_FOR_APP_VIA_TOXIPROXY
        ))) {
            NewTopic newTopic = new NewTopic(TEST_TOPIC_NAME, 1, (short) 1);
            try {
                adminClient.createTopics(List.of(newTopic)).all().get(30, TimeUnit.SECONDS);
                log.info("Kafka topic '{}' created successfully.", TEST_TOPIC_NAME);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TopicExistsException) {
                    log.warn("Kafka topic '{}' already exists.", TEST_TOPIC_NAME);
                } else {
                    log.error("Failed to create Kafka topic '{}'.", TEST_TOPIC_NAME, e);
                    throw new RuntimeException("Failed to create Kafka topic", e.getCause());
                }
            } catch (InterruptedException | TimeoutException e) {
                Thread.currentThread().interrupt();
                log.error("Timed out or interrupted while creating Kafka topic '{}'.", TEST_TOPIC_NAME, e);
                throw new RuntimeException("Timed out or interrupted while creating Kafka topic", e);
            }
        }
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
        undeliveredRepository.deleteAllInBatch();
        registerUserApiUrl = "http://localhost:" + applicationPort + ApiConstants.REGISTER_ENDPOINT;

        try {
            enableKafkaProxy();
        } catch (IOException e) {
            log.error("Failed to enable Kafka proxy before metadata validation", e);
            throw new RuntimeException("Setup failure: could not enable Kafka proxy", e);
        }

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS_FOR_APP_VIA_TOXIPROXY);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "it-reg-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.tasktracker.backend.user.messaging.dto");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EmailTriggerCommand.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "3000");
        consumerProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "1000");
        consumerProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "3500");
        consumerProps.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "4000");

        testKafkaConsumer = new KafkaConsumer<>(consumerProps);
        testKafkaConsumer.subscribe(Collections.singletonList(TEST_TOPIC_NAME));

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(1000))
                .untilAsserted(() -> {
                    testKafkaConsumer.poll(Duration.ofMillis(900));
                    assertThat(testKafkaConsumer.assignment()).as("Consumer assignment").isNotEmpty();
                });
        log.info("Test consumer assigned partitions: {}", testKafkaConsumer.assignment());

        ConsumerRecords<String, EmailTriggerCommand> initialRecords = testKafkaConsumer.poll(Duration.ofMillis(500));
        log.info("Test consumer initial poll (latest offset) consumed {} records.", initialRecords.count());
        assertThat(initialRecords.count()).as("No records for new test consumer group at 'latest'").isZero();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (testKafkaConsumer != null) {
            testKafkaConsumer.close(Duration.ofMillis(500));
        }
        enableKafkaProxy();
        MDC.clear();
    }

    private void enableKafkaProxy() throws IOException {
        clearAllToxics();
        if (!kafkaToToxiproxyProxy.isEnabled()) {
            kafkaToToxiproxyProxy.enable();
        }
        log.info("Kafka proxy ENABLED and all toxics cleared.");
    }

    private void clearAllToxics() throws IOException {
        for (Toxic toxic : kafkaToToxiproxyProxy.toxics().getAll()) {
            try {
                toxic.remove();
            } catch (IOException e) {
                log.warn("Failed to remove toxic {} from proxy {}: {}", toxic.getName(), kafkaToToxiproxyProxy.getName(), e.getMessage());
            }
        }
    }

    private HttpEntity<RegisterRequest> createHttpEntity(RegisterRequest payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    @Test
    @DisplayName("TC_IT_REG_KAFKA_01: Успешная регистрация -> пользователь создан, токен выдан, Kafka-сообщение отправлено")
    void registerUser_whenSuccessful_thenUserCreatedTokenIssuedAndKafkaMessageSent() {
        RegisterRequest request = new RegisterRequest("kafka-user01@example.com", "Password123!", "Password123!");
        HttpEntity<RegisterRequest> entity = createHttpEntity(request);

        ResponseEntity<AuthResponse> responseEntity = testRestTemplate.postForEntity(registerUserApiUrl, entity, AuthResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getAccessToken()).isNotBlank();
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        assertThat(userOptional).isPresent();
        User createdUser = userOptional.get();

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ConsumerRecords<String, EmailTriggerCommand> records = testKafkaConsumer.poll(Duration.ofMillis(400));
                    assertThat(records.count()).as("Kafka records for " + request.getEmail()).isEqualTo(1);
                    ConsumerRecord<String, EmailTriggerCommand> record = records.iterator().next();
                    EmailTriggerCommand command = record.value();
                    assertThat(command.getRecipientEmail()).isEqualTo(request.getEmail());
                    assertThat(command.getTemplateId()).isEqualTo("USER_WELCOME");
                    assertThat(command.getUserId()).isEqualTo(createdUser.getId());
                    assertThat(command.getCorrelationId()).isNotNull();
                });
    }

    @Test
    @DisplayName("TC_IT_REG_KAFKA_02: Email уже существует -> 409 Conflict, Kafka-сообщение НЕ отправлено")
    void registerUser_whenEmailExists_thenConflictAndNoKafkaMessage() {
        userRepository.save(new User(null, "duplicate02@example.com", "password", Instant.now(), Instant.now()));
        RegisterRequest request = new RegisterRequest("duplicate02@example.com", "Password123!", "Password123!");
        HttpEntity<RegisterRequest> entity = createHttpEntity(request);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(registerUserApiUrl, entity, ProblemDetail.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ConsumerRecords<String, EmailTriggerCommand> records = testKafkaConsumer.poll(Duration.ofSeconds(1)); // Уменьшил ожидание
        assertThat(records.count()).as("Kafka records for duplicate email").isZero();
    }

    @Test
    @DisplayName("TC_IT_REG_KAFKA_03: Пароли не совпадают -> 400 Bad Request, Kafka-сообщение НЕ отправлено")
    void registerUser_whenPasswordsMismatch_thenBadRequestAndNoKafkaMessage() {
        RegisterRequest request = new RegisterRequest("mismatch03@example.com", "Password123!", "DifferentPassword!");
        HttpEntity<RegisterRequest> entity = createHttpEntity(request);

        ResponseEntity<ProblemDetail> responseEntity = testRestTemplate.postForEntity(registerUserApiUrl, entity, ProblemDetail.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ConsumerRecords<String, EmailTriggerCommand> records = testKafkaConsumer.poll(Duration.ofSeconds(1));
        assertThat(records.count()).as("Kafka records for password mismatch").isZero();
    }

    @Test
    @DisplayName("TC_IT_REG_KAFKA_04: Kafka недоступен -> регистрация успешна, сообщение в fallback-таблице")
    void registerUser_whenKafkaUnavailable_thenRegistrationSucceedsAndMessageInFallback() throws IOException, InterruptedException {
        RegisterRequest request = new RegisterRequest("fallback04@example.com", "Password123!", "Password123!");
        HttpEntity<RegisterRequest> entity = createHttpEntity(request);

        kafkaToToxiproxyProxy.toxics().timeout("CONNECTION_CUT_TIMEOUT_UP", ToxicDirection.UPSTREAM, 0); // 0ms timeout = connection reset
        kafkaToToxiproxyProxy.toxics().timeout("CONNECTION_CUT_TIMEOUT_DOWN", ToxicDirection.DOWNSTREAM, 0);
        log.info("Applied TIMEOUT(0) toxics to kafka-proxy to simulate Kafka unavailability.");
        // Небольшая пауза, чтобы токсины точно применились
        Thread.sleep(200);

        ResponseEntity<AuthResponse> responseEntity = testRestTemplate.postForEntity(registerUserApiUrl, entity, AuthResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        assertThat(userOptional).isPresent();
        User createdUser = userOptional.get();
        Long createdUserId = createdUser.getId();

        ConsumerRecords<String, EmailTriggerCommand> kafkaRecords = null;
        try {
            kafkaRecords = testKafkaConsumer.poll(Duration.ofMillis(500)); // Короткий таймаут
        } catch (Exception e) {
            log.warn("Expected exception while polling Kafka when proxy is simulating unavailability: {}", e.getMessage());
        }

        assertThat(kafkaRecords.count()).as("Kafka records when Kafka is down").isZero();

        await()
                .pollDelay(Duration.ofMillis(2200))
                .pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    List<UndeliveredWelcomeEmail> fallbackCommands = undeliveredRepository.findAll();
                    assertThat(fallbackCommands).hasSize(1);
                    UndeliveredWelcomeEmail fallbackCommand = fallbackCommands.getFirst();

                    // Проверяем поля в соответствии с новой сущностью UndeliveredWelcomeEmail
                    assertThat(fallbackCommand.getUserId())
                            .as("User ID in fallback (Primary Key)")
                            .isEqualTo(createdUserId);

                    assertThat(fallbackCommand.getRecipientEmail())
                            .as("Recipient Email in fallback")
                            .isEqualTo(request.getEmail());

                    assertThat(fallbackCommand.getLocale())
                            .as("Locale in fallback")
                            .isNotNull(); // Проверяем, что локаль была захвачена и сохранена

                    assertThat(fallbackCommand.getLastAttemptTraceId())
                            .as("Last Attempt Trace ID in fallback")
                            .isNotNull().isNotBlank(); // Проверяем новое имя поля

                    assertThat(fallbackCommand.getInitialAttemptAt())
                            .as("Initial Attempt At in fallback")
                            .isNotNull();

                    assertThat(fallbackCommand.getLastAttemptAt())
                            .as("Last Attempt At in fallback")
                            .isNotNull();

                    assertThat(fallbackCommand.getRetryCount())
                            .as("Retry Count in fallback")
                            .isZero();

                    assertThat(fallbackCommand.getDeliveryErrorMessage())
                            .as("Delivery Error Message in fallback")
                            .isNotNull().isNotBlank();
                });
    }

    @Test
    @DisplayName("TC_IT_REG_KAFKA_05: Kafka кратковременно недоступен, но восстанавливается для ретрая")
    void registerUser_whenKafkaTemporarilyUnavailableButRecoversForRetry_thenMessageSent() throws Exception {
        RegisterRequest request = new RegisterRequest("retry-user@example.com", "Password123!", "Password123!");
        HttpEntity<RegisterRequest> entity = createHttpEntity(request);

        Latency tempLatency = kafkaToToxiproxyProxy.toxics().latency("temp_latency", ToxicDirection.UPSTREAM, 1100);// request.timeout.ms
        log.info("Kafka proxy TEMPORARILY DEGRADED/DISABLED.");

        ResponseEntity<AuthResponse> responseEntity = testRestTemplate.postForEntity(registerUserApiUrl, entity, AuthResponse.class);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Небольшая пауза, чтобы первая попытка точно начала обрабатываться продюсером
        Thread.sleep(1000); // Даем время на первую попытку send()

        tempLatency.setLatency(0); //release
        log.info("Kafka proxy RE-ENABLED.");

        // 4. Проверить, что сообщение в итоге дошло до Kafka
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        assertThat(userOptional).isPresent();

        await()
                .pollDelay(Duration.ofMillis(300)) //Thread was slept
                .pollInterval(Duration.ofMillis(500))
                .atMost(Duration.ofMillis(2200)) // Ждем чуть дольше delivery.timeout
                .untilAsserted(() -> {
                    ConsumerRecords<String, EmailTriggerCommand> records = testKafkaConsumer.poll(Duration.ofMillis(400));
                    assertThat(records.count()).as("Kafka records for " + request.getEmail()).isEqualTo(1);
                    ConsumerRecord<String, EmailTriggerCommand> record = records.iterator().next();
                    EmailTriggerCommand command = record.value();
                    assertThat(command.getRecipientEmail()).isEqualTo(request.getEmail());
                });

        // 5. Проверить, что в fallback-таблице пусто
        assertThat(undeliveredRepository.findAll()).isEmpty();
    }
}