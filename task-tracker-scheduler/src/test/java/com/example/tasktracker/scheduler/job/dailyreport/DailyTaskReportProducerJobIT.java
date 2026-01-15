package com.example.tasktracker.scheduler.job.dailyreport;

import com.example.tasktracker.scheduler.job.JobStateRepository;
import com.example.tasktracker.scheduler.job.dailyreport.config.DailyReportJobProperties;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.job.dto.CursorPayload;
import com.example.tasktracker.scheduler.job.dto.JobExecutionState;
import com.example.tasktracker.scheduler.job.dto.JobStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.scheduling.config.ScheduledTaskRegistrar.CRON_DISABLED;

@SpringBootTest
@ActiveProfiles("ci")
@Testcontainers
@DisplayName("Daily Report Producer Job")
class DailyTaskReportProducerJobIT {

    @Container
    static final RedisContainer redis = new RedisContainer(
            DockerImageName.parse("redis:8-alpine"));

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:4.1.1"))
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1));

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.scheduler.jobs.daily-task-reports.enabled", () -> "true");
        registry.add("app.scheduler.jobs.daily-task-reports.cron", () -> CRON_DISABLED); //disable trigger autostart

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        registry.add("app.scheduler.backend-client.url", wireMockServer::baseUrl);
    }

    @Autowired
    private DailyReportJobProperties dailyReportJobProperties;

    @Autowired
    private DailyTaskReportJobTrigger jobTrigger;

    @Autowired
    private JobStateRepository jobStateRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private Clock clock;

    @Value("${app.scheduler.jobs.daily-task-reports.kafka-topic-name}")
    private String topicName;

    // Kafka Consumer для проверки
    private KafkaMessageListenerContainer<String, UserSelectedForDailyReportEvent> container;
    private final BlockingQueue<ConsumerRecord<String, UserSelectedForDailyReportEvent>> records = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException, TimeoutException {
        createKafkaTopic(topicName);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, UserSelectedForDailyReportEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(UserSelectedForDailyReportEvent.class, false) // false = use headers off (если продюсер не шлет)
        );

        ContainerProperties containerProperties = new ContainerProperties(topicName);
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setupMessageListener((MessageListener<String, UserSelectedForDailyReportEvent>) records::add);
        container.start();
    }

    @AfterEach
    void tearDown() {
        container.stop();
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        records.clear();
    }

    private void createKafkaTopic(String topicName) throws ExecutionException, InterruptedException, TimeoutException {
        try (AdminClient adminClient = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
            try {
                adminClient.createTopics(Collections.singletonList(newTopic)).all().get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TopicExistsException) {
                    // Топик уже существует, это нормально для ретраев при запуске тестов
                    return;
                }
                throw e; // Пробросить любую другую ошибку
            }
        }
    }

    @Test
    @DisplayName("Happy Path: полный цикл от API до Kafka и Redis")
    void executeJob_shouldFetchFromApiAndProduceToKafkaAndSaveState() {
        // 1. Arrange: Настройка WireMock (Stubbing)
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/internal/scheduler-support/user-ids"))
                .withQueryParam("limit", equalTo("1000"))
                .withQueryParam("cursor", absent())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "userIds": [101, 102],
                                  "pageInfo": {
                                    "hasNextPage": true,
                                    "nextPageCursor": "next_page_cursor"
                                  }
                                }
                                """)));

        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/internal/scheduler-support/user-ids"))
                .withQueryParam("cursor", equalTo("next_page_cursor"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "userIds": [103],
                                  "pageInfo": {
                                    "hasNextPage": false,
                                    "nextPageCursor": null
                                  }
                                }
                                """)));

        LocalDate reportDate = LocalDate.now(clock).minusDays(1);

        // 2. Act: Запуск джобы
        jobTrigger.trigger();

        // 3. Assert: Kafka (ждем 3 сообщения)
        await().atMost(10, TimeUnit.SECONDS).until(() -> records.size() == 3);

        ConsumerRecord<String, UserSelectedForDailyReportEvent> rec1 = records.poll();
        assertThat(rec1.value().userId()).isEqualTo(101L);
        assertThat(rec1.value().reportDate()).isEqualTo(reportDate);
        assertThat(rec1.key()).isNull(); // Проверяем, что ключ null (Round Robin)

        // 4. Assert: Redis
        var stateOptional = jobStateRepository.findState(
                dailyReportJobProperties.getJobName(),
                reportDate,
                new TypeReference<JobExecutionState<CursorPayload>>() {
                }
        );

        assertThat(stateOptional).isPresent();
        assertThat(stateOptional.get().status()).isEqualTo(JobStatus.PUBLISHED);
    }
}