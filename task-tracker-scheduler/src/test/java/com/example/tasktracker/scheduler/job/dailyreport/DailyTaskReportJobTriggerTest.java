package com.example.tasktracker.scheduler.job.dailyreport;

import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import com.example.tasktracker.scheduler.job.dailyreport.config.DailyReportJobProperties;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты для DailyTaskReportJobTrigger")
class DailyTaskReportJobTriggerTest {

    @Mock
    private LockingTaskExecutor mockLockingTaskExecutor;
    @Mock
    private DailyTaskReportProducerJob mockExecutionJob;
    @Mock
    private DailyReportJobProperties mockJobProperties;

    // Фиксированное время: 2025-07-15 10:00 UTC
    private final Clock fixedClock = Clock.fixed(Instant.parse("2025-07-15T10:00:00Z"), ZoneOffset.UTC);

    private DailyTaskReportJobTrigger trigger;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor
    private ArgumentCaptor<LockConfiguration> lockConfigCaptor;

    private static final String JOB_NAME = "daily-reports-job";

    @BeforeEach
    void setUp() {
        // Настраиваем мок свойств
        SchedulerAppProperties.ShedLockProperties shedLockProps = new SchedulerAppProperties.ShedLockProperties();
        shedLockProps.setLockAtMostFor(Duration.ofMinutes(30));
        shedLockProps.setLockAtLeastFor(Duration.ofSeconds(60));

        when(mockJobProperties.getJobName()).thenReturn(JOB_NAME);
        when(mockJobProperties.getShedlock()).thenReturn(shedLockProps);

        trigger = new DailyTaskReportJobTrigger(mockLockingTaskExecutor, mockExecutionJob, mockJobProperties, fixedClock);
    }

    @Test
    @DisplayName("TC-01: trigger() должен вызвать LockingTaskExecutor с корректной конфигурацией блокировки")
    void trigger_shouldCallExecutorWithCorrectLockConfiguration() {
        // Act
        trigger.trigger();

        // Assert
        verify(mockLockingTaskExecutor).executeWithLock(any(Runnable.class), lockConfigCaptor.capture());
        LockConfiguration capturedConfig = lockConfigCaptor.getValue();

        assertThat(capturedConfig.getName()).isEqualTo(JOB_NAME);
        assertThat(capturedConfig.getLockAtMostUntil()).isEqualTo(fixedClock.instant().plus(Duration.ofMinutes(30)));
        assertThat(capturedConfig.getLockAtLeastUntil()).isEqualTo(fixedClock.instant().plus(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("TC-02: Выполняемый Runnable должен вызвать сервис с датой 'вчера'")
    void trigger_runnableShouldCallServiceWithYesterdayDate() {
        // Arrange
        // Ожидаемая дата отчета: 2025-07-14 (вчера относительно 2025-07-15)
        LocalDate expectedReportDate = LocalDate.of(2025, 7, 14);

        // Act
        trigger.trigger();

        // Assert
        // Перехватываем Runnable, который был передан в ShedLock
        verify(mockLockingTaskExecutor).executeWithLock(runnableCaptor.capture(), any(LockConfiguration.class));
        Runnable task = runnableCaptor.getValue();

        // Выполняем Runnable вручную
        task.run();

        // Проверяем, что бизнес-логика была вызвана с правильной датой
        verify(mockExecutionJob, times(1)).execute(eq(expectedReportDate));
    }
}