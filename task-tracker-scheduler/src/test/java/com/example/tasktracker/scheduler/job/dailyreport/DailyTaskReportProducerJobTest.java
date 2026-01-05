package com.example.tasktracker.scheduler.job.dailyreport;

import com.example.tasktracker.scheduler.client.dto.PageInfo;
import com.example.tasktracker.scheduler.job.JobStateRepository;
import com.example.tasktracker.scheduler.job.dto.JobExecutionState;
import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import com.example.tasktracker.scheduler.job.dailyreport.client.UserIdsFetcherClient;
import com.example.tasktracker.scheduler.job.dailyreport.client.dto.PaginatedUserIdsResponse;
import com.example.tasktracker.scheduler.job.dailyreport.config.DailyReportJobProperties;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import net.javacrumbs.shedlock.core.LockExtender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Unit-тесты для DailyTaskReportProducerJob")
class DailyTaskReportProducerJobTest {

    @Mock private JobStateRepository mockJobStateRepository;
    @Mock private UserIdsFetcherClient mockUserIdsFetcherClient;
    @Mock private KafkaTemplate<String, UserSelectedForDailyReportEvent> mockKafkaTemplate;
    @Mock private Executor mockKafkaCallbackExecutor;
    @Mock private DailyReportJobProperties mockJobProperties;
    @Mock private Timer mockTimer;
    @Mock private MetricsReporter mockMetrics;

    private DailyTaskReportProducerJob job;
    private MockedStatic<LockExtender> lockExtenderMockedStatic;

    private static final String JOB_NAME = "daily-reports";
    private static final String TOPIC_NAME = "test-topic";
    private static final LocalDate REPORT_DATE = LocalDate.of(2025, 7, 15);
    private static final int PAGE_SIZE = 100;

    @BeforeEach
    void setUp() {
        when(mockJobProperties.getJobName()).thenReturn(JOB_NAME);
        when(mockJobProperties.getKafkaTopicName()).thenReturn(TOPIC_NAME);
        when(mockJobProperties.getPageSize()).thenReturn(PAGE_SIZE);

        SchedulerAppProperties.ShedLockProperties shedLockProps = new SchedulerAppProperties.ShedLockProperties();
        shedLockProps.setLockAtMostFor(Duration.ofMinutes(10));
        shedLockProps.setLockAtLeastFor(Duration.ofSeconds(1));
        when(mockJobProperties.getShedlock()).thenReturn(shedLockProps);

        when(mockMetrics.getTimer(any(), any())).thenReturn(mockTimer);

        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(mockKafkaCallbackExecutor).execute(any(Runnable.class));

        job = new DailyTaskReportProducerJob(
                mockJobStateRepository,
                mockUserIdsFetcherClient,
                mockKafkaTemplate,
                mockKafkaCallbackExecutor,
                mockJobProperties,
                mockMetrics
        );

        lockExtenderMockedStatic = mockStatic(LockExtender.class);
    }

    @AfterEach
    void tearDown() {
        lockExtenderMockedStatic.close();
    }

    @Nested
    @DisplayName("Проверка состояния")
    class StateCheckTests {
        @Test
        @DisplayName("TC-01: Если состояние PUBLISHED, джоба должна завершиться без действий")
        void execute_whenPublished_shouldDoNothing() {
            // Arrange
            when(mockJobStateRepository.findState(eq(JOB_NAME), eq(REPORT_DATE), any(TypeReference.class)))
                    .thenReturn(Optional.of(JobExecutionState.published()));

            // Act
            job.execute(REPORT_DATE);

            // Assert
            verify(mockUserIdsFetcherClient, never()).fetchUserIds(any(), anyInt());
        }

        @Test
        @DisplayName("TC-02: Если состояние FAILED, джоба должна завершиться (требует вмешательства)")
        void execute_whenFailed_shouldDoNothing() {
            // Arrange
            when(mockJobStateRepository.findState(eq(JOB_NAME), eq(REPORT_DATE), any(TypeReference.class)))
                    .thenReturn(Optional.of(JobExecutionState.failed("error")));

            // Act
            job.execute(REPORT_DATE);

            // Assert
            verify(mockUserIdsFetcherClient, never()).fetchUserIds(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("Сценарии отказов (Failure Scenarios)")
    class FailureTests {
        @Test
        @DisplayName("TC-05: Сбой Kafka -> аварийная остановка, состояние НЕ сохраняется")
        void execute_whenKafkaFails_shouldAbortAndNotSaveState() {
            // Arrange
            when(mockJobStateRepository.findState(any(), any(), any())).thenReturn(Optional.empty());

            PaginatedUserIdsResponse page = new PaginatedUserIdsResponse(List.of(1L, 2L), new PageInfo(true, "next_cursor"));
            when(mockUserIdsFetcherClient.fetchUserIds(isNull(), eq(PAGE_SIZE))).thenReturn(page);

            // Kafka падает для одного из сообщений
            when(mockKafkaTemplate.send(any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class))) // Успех для 1L
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down"))); // Провал для 2L

            // Act
            job.execute(REPORT_DATE);

            // Assert
            // 1. Метрика сбоя отправки
            verify(mockMetrics).incrementCounter(eq(Metric.JOB_KAFKA_SEND_FAILURE), any(Tags.class));

            // 2. Метрика сбоя джобы (в catch блоке)
            verify(mockMetrics).incrementCounter(eq(Metric.JOB_RUN_FAILURE), any(Tags.class));

            // 3. Самое важное: saveState НЕ должен быть вызван. Никакого прогресса не фиксируем.
            verify(mockJobStateRepository, never()).saveState(any(), any(), any());
        }

        @Test
        @DisplayName("TC-06: Сбой API -> джоба падает, состояние не меняется")
        void execute_whenApiFails_shouldAbort() {
            when(mockJobStateRepository.findState(any(), any(), any())).thenReturn(Optional.empty());
            when(mockUserIdsFetcherClient.fetchUserIds(any(), anyInt())).thenThrow(new RuntimeException("API error"));

            job.execute(REPORT_DATE);

            verify(mockMetrics).incrementCounter(eq(Metric.JOB_RUN_FAILURE), any(Tags.class));
            verify(mockJobStateRepository, never()).saveState(any(), any(), any());
        }

        @Test
        @DisplayName("TC-07: Потеря блокировки -> джоба падает")
        void execute_whenLockLost_shouldAbort() {
            when(mockJobStateRepository.findState(any(), any(), any())).thenReturn(Optional.empty());

            // Мокаем статик, чтобы выбросить исключение при попытке продлить лок
            lockExtenderMockedStatic.when(() -> LockExtender.extendActiveLock(any(), any()))
                    .thenThrow(new LockExtender.NoActiveLockException());

            job.execute(REPORT_DATE);

            verify(mockUserIdsFetcherClient, never()).fetchUserIds(any(), anyInt()); // До фетча не дошли
            verify(mockMetrics).incrementCounter(eq(Metric.JOB_RUN_FAILURE), any(Tags.class));
        }
    }
}