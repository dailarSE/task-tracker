package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.o11y.observation.annotation.ObservedExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@RequiredArgsConstructor
public class PipelineConfig {
    private final ReliabilityProperties reliabilityProperties;

    /**
     * Очередь размером maxConnections нужна как буфер для сглаживания джиттера.
     * Без очереди происходят контринтуитивные реджекты: результат задачи доступен раньше, чем освобождается поток.
     * Теоретическое максимальное количество конфликтующих потоков равно pool size.
     */
    @ObservedExecutor("email.sender.smtp.executor")
    @Bean("smtpExecutor")
    public ExecutorService smtpExecutor() {
        int maxConnections = reliabilityProperties.getCapacity().getMaxActiveConnections();

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "mail-smtp-sender-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };

        return new ThreadPoolExecutor(
                maxConnections,
                maxConnections,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(maxConnections),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );

    }

    @ObservedExecutor(value = "email.sender.vthread.executor", metrics = false)
    @Bean("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}
