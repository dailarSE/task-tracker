package com.example.tasktracker.scheduler.config;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@Slf4j
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(
            RedisConnectionFactory connectionFactory,
            @Value("${spring.application.name}") String serviceName) {
        String lockKeyPrefix = "shedlock:" + serviceName;
        log.info("Configuring ShedLock with Redis. Lock key prefix: '{}'", lockKeyPrefix);
        return new RedisLockProvider(connectionFactory, lockKeyPrefix);
    }

    /**
     * Создает бин LockingTaskExecutor, который будет использоваться для
     * программного управления долгоживущими блокировками с возможностью их продления.
     *
     * @param lockProvider Провайдер блокировок (Redis).
     * @return Экземпляр LockingTaskExecutor.
     */
    @Bean
    public LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }


}