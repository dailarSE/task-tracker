package com.example.tasktracker.scheduler.consumer.dailyreport;

import com.example.tasktracker.scheduler.consumer.dailyreport.client.dto.TaskInfo;
import com.example.tasktracker.scheduler.consumer.dailyreport.client.dto.UserTaskReport;
import com.example.tasktracker.scheduler.consumer.dailyreport.component.DailyReportMapper;
import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.dto.EmailTriggerCommand;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@Slf4j
@SpringBootTest
@Testcontainers
@ActiveProfiles("ci")
@DisplayName("Test for DailyTaskReportConsumer")
class DailyTaskReportConsumerIT {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

    @RegisterExtension
    static final WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.scheduler.consumers.daily-report.enabled", () -> "true");

        registry.add("spring.kafka.consumer.properties.allow.auto.create.topics", () -> "false");
        registry.add("spring.kafka.producer.properties.allow.auto.create.topics", () -> "false");

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.scheduler.backend-client.url", wireMockServer::baseUrl);
        registry.add("app.scheduler.consumers.daily-report.concurrency", () -> "1");

        registry.add("app.scheduler.consumers.daily-report.retry-and-dlt.max-attempts", DailyTaskReportConsumerIT::getRetryMaxAttempts);
        registry.add("app.scheduler.consumers.daily-report.retry-and-dlt.initial-interval-ms", () -> "100");
        registry.add("app.scheduler.consumers.daily-report.retry-and-dlt.max-interval-ms", () -> "500");
        registry.add("app.scheduler.consumers.daily-report.retry-and-dlt.multiplier", () -> "2.0");
        // Отключаем Redis, он не нужен для этого теста
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
    }

    @MockitoSpyBean
    private DailyReportMapper dailyReportMapperSpy;
    @Autowired
    private KafkaTemplate<String, UserSelectedForDailyReportEvent> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.scheduler.consumers.daily-report.topic-name}")
    private String sourceTopic;
    @Value("${app.scheduler.consumers.daily-report.sink-topic-name}")
    private String sinkTopic;

    private KafkaMessageListenerContainer<String, EmailTriggerCommand> resultListenerContainer;
    private final BlockingQueue<ConsumerRecord<String, EmailTriggerCommand>> receivedCommands = new LinkedBlockingQueue<>();

    private KafkaMessageListenerContainer<String, UserSelectedForDailyReportEvent> dltListenerContainer;
    private final BlockingQueue<ConsumerRecord<String, UserSelectedForDailyReportEvent>> receivedDltEvents = new LinkedBlockingQueue<>();

    private static String getRetryMaxAttempts() {
        return "3";
    }

    @BeforeEach
    void setUpListeners() throws Exception {
        recreateTopics();

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        JsonDeserializer<EmailTriggerCommand> deserializer = new JsonDeserializer<>(EmailTriggerCommand.class, false);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, EmailTriggerCommand> consumerFactory = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), deserializer);

        ContainerProperties containerProperties = new ContainerProperties(sinkTopic);
        resultListenerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        resultListenerContainer.setupMessageListener((MessageListener<String, EmailTriggerCommand>) receivedCommands::add);
        resultListenerContainer.start();

        ContainerTestUtils.waitForAssignment(resultListenerContainer, 1);

        //dlt
        Map<String, Object> dltProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-dlt-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );
        JsonDeserializer<UserSelectedForDailyReportEvent> dltDeserializer =
                new JsonDeserializer<>(UserSelectedForDailyReportEvent.class, false);
        dltDeserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, UserSelectedForDailyReportEvent> dltFactory = new DefaultKafkaConsumerFactory<>(
                dltProps, new StringDeserializer(), dltDeserializer);

        ContainerProperties dltContainerProps = new ContainerProperties(sourceTopic + "-dlt");
        dltListenerContainer = new KafkaMessageListenerContainer<>(dltFactory, dltContainerProps);
        dltListenerContainer.setupMessageListener((MessageListener<String, UserSelectedForDailyReportEvent>) receivedDltEvents::add);
        dltListenerContainer.start();

        ContainerTestUtils.waitForAssignment(dltListenerContainer, 1);
    }

    private void recreateTopics() throws Exception {
        try (AdminClient adminClient = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            List<String> topicsToDelete = List.of(sourceTopic, sinkTopic, sourceTopic+"-dlt");

            Set<String> existingTopics = adminClient.listTopics().names().get(10, TimeUnit.SECONDS);
            List<String> topicsThatExist = topicsToDelete.stream().filter(existingTopics::contains).toList();

            if (!topicsThatExist.isEmpty()) {
                adminClient.deleteTopics(topicsThatExist).all().get(10, TimeUnit.SECONDS);

                // Важно: ждем, пока топики реально удалятся
                await().atMost(10, TimeUnit.SECONDS)
                        .pollInterval(Duration.ofMillis(200))
                        .until(() -> {
                            Set<String> remainingTopics = adminClient.listTopics().names().get();
                            return remainingTopics.stream().noneMatch(topicsThatExist::contains);
                        });
            }

            // Пересоздаем
            List<NewTopic> topicsToCreate = topicsToDelete.stream().map(name -> new NewTopic(name, 1, (short) 1)).toList();
            adminClient.createTopics(topicsToCreate).all().get(10, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    void tearDown() {
        resultListenerContainer.stop();
        dltListenerContainer.stop();
        receivedCommands.clear();
        receivedDltEvents.clear();
        wireMockServer.resetAll();
        Mockito.reset(dailyReportMapperSpy);
    }

    private EmailTriggerCommand findCommandForUser(long userId) {
        return receivedCommands.stream()
                .map(ConsumerRecord::value)
                .filter(command -> command.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Command for user " + userId + " not found"));
    }

    @Test
    @DisplayName("TC-1: Happy Path - should process events, fetch reports, and publish commands")
    void happyPath_shouldProcessBatchAndPublishCommands() throws JsonProcessingException {
        // --- ARRANGE ---
        final String jobRunId = UUID.randomUUID().toString();
        final LocalDate reportDate = LocalDate.of(2025, 12, 25);
        final long userId1 = 101L;
        final long userId2 = 102L;
        final String userEmail1 = "user101@test.com";
        final String userEmail2 = "user102@test.com";
        final String expectedTemplateId = "DAILY_TASK_REPORT";

        List<UserSelectedForDailyReportEvent> sourceEvents = List.of(
                new UserSelectedForDailyReportEvent(userId1, jobRunId, reportDate),
                new UserSelectedForDailyReportEvent(userId2, jobRunId, reportDate)
        );

        UserTaskReport report101 = new UserTaskReport(userId1, "user101@test.com", List.of(new TaskInfo(1L, "Done task")), List.of());
        UserTaskReport report102 = new UserTaskReport(userId2, "user102@test.com", List.of(), List.of(new TaskInfo(2L, "Pending task")));

        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports"))
                // Сценарий 1: Пришел батч из двух ID
                .withRequestBody(matchingJsonPath("$[?(@.userIds contains 101 && @.userIds contains 102)]"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(List.of(report101, report102)))));

        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports"))
                // Сценарий 2: Пришел только ID 101
                .withRequestBody(matchingJsonPath("$.userIds[?(@ == 101)]"))
                .withRequestBody(notMatching(".*102.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(List.of(report101)))));

        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports"))
                // Сценарий 3: Пришел только ID 102
                .withRequestBody(matchingJsonPath("$.userIds[?(@ == 102)]"))
                .withRequestBody(notMatching(".*101.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(List.of(report102)))));


        // --- ACT ---
        sourceEvents.forEach(event -> kafkaTemplate.send(sourceTopic, event));

        // --- ASSERT ---
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
                .until(() -> receivedCommands.size() >= 2);

        wireMockServer.verify(new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN_OR_EQUAL, 1),
                postRequestedFor(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports")));
        wireMockServer.verify(new CountMatchingStrategy(CountMatchingStrategy.LESS_THAN_OR_EQUAL, 2),
                postRequestedFor(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports")));

        EmailTriggerCommand command1 = findCommandForUser(userId1);
        assertThat(command1.recipientEmail()).isEqualTo(userEmail1);
        assertThat(command1.templateId()).isEqualTo(expectedTemplateId);
        assertThat(command1.templateContext().get("tasksCompleted"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(1).extracting("title").contains("Done task");

        EmailTriggerCommand command2 = findCommandForUser(userId2);
        assertThat(command2.recipientEmail()).isEqualTo(userEmail2);
        assertThat(command2.templateContext().get("tasksPending"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(1).extracting("title").contains("Pending task");
    }

    @Test
    @DisplayName("TC-2: Global Failure - should retry and then send all events to DLT")
    void globalFailure_shouldRetryAndSendAllEventsToDlt() {
        // --- ARRANGE ---
        final long userId1 = 201L;
        final long userId2 = 202L;
        final int maxAttempts = Integer.parseInt(getRetryMaxAttempts());
        List<UserSelectedForDailyReportEvent> sourceEvents = List.of(
                new UserSelectedForDailyReportEvent(userId1, UUID.randomUUID().toString(), LocalDate.now()),
                new UserSelectedForDailyReportEvent(userId2, UUID.randomUUID().toString(), LocalDate.now())
        );

        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports"))
                .willReturn(aResponse().withStatus(503)));

        // --- ACT ---
        sourceEvents.forEach(event -> kafkaTemplate.send(sourceTopic, event));

        // --- ASSERT ---
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    wireMockServer.verify(new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN_OR_EQUAL, maxAttempts),
                            postRequestedFor(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports")));

                    assertThat(receivedCommands).isEmpty();

                    Set<Long> dltUserIds = receivedDltEvents.stream()
                            .map(ConsumerRecord::value)
                            .map(UserSelectedForDailyReportEvent::userId)
                            .collect(Collectors.toSet());

                    assertThat(dltUserIds).containsExactlyInAnyOrder(userId1, userId2);
                });
    }

    @Test
    @DisplayName("TC-3: Partial Failure - should send failed item to DLT and process others")
    void partialFailure_shouldSendFailedItemToDltAndProcessOthers() throws JsonProcessingException {
        // --- ARRANGE ---
        final String jobRunId = UUID.randomUUID().toString();
        final LocalDate reportDate = LocalDate.of(2025, 12, 26);
        final long healthyUserId1 = 301L;
        final long poisonPillUserId = 302L; // Этот пользователь вызовет ошибку
        final long healthyUserId2 = 303L;

        List<UserSelectedForDailyReportEvent> sourceEvents = List.of(
                new UserSelectedForDailyReportEvent(healthyUserId1, jobRunId, reportDate),
                new UserSelectedForDailyReportEvent(poisonPillUserId, jobRunId, reportDate),
                new UserSelectedForDailyReportEvent(healthyUserId2, jobRunId, reportDate)
        );

        List<UserTaskReport> backendResponse = List.of(
                new UserTaskReport(healthyUserId1, "user301@test.com", List.of(), List.of()),
                new UserTaskReport(poisonPillUserId, "poison@test.com", List.of(), List.of()),
                new UserTaskReport(healthyUserId2, "user303@test.com", List.of(), List.of())
        );
        wireMockServer.stubFor(post(urlPathEqualTo("/api/v1/internal/scheduler-support/tasks/user-reports"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(backendResponse))));

        doThrow(new RuntimeException("Simulated mapping error for user " + poisonPillUserId))
                .when(dailyReportMapperSpy)
                .toCommand(argThat(report -> report.userId().equals(poisonPillUserId)), Mockito.any(LocalDate.class));

        dltListenerContainer.stop();

        // --- ACT ---
        sourceEvents.forEach(event -> kafkaTemplate.send(sourceTopic, event));
        dltListenerContainer.start();

        // --- ASSERT ---
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    Set<Long> sinkUserIds = receivedCommands.stream()
                            .map(ConsumerRecord::value)
                            .map(EmailTriggerCommand::userId)
                            .collect(Collectors.toSet());
                    assertThat(sinkUserIds)
                            .withFailMessage("Sink topic should contain only healthy user IDs")
                            .containsExactlyInAnyOrder(healthyUserId1, healthyUserId2);

                    Set<Long> dltUserIds = receivedDltEvents.stream()
                            .map(ConsumerRecord::value)
                            .map(UserSelectedForDailyReportEvent::userId)
                            .collect(Collectors.toSet());
                    assertThat(dltUserIds)
                            .withFailMessage("DLT should contain only the poison pill user ID")
                            .containsExactlyInAnyOrder(poisonPillUserId);
                });
    }
}