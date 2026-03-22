package com.example.tasktracker.emailsender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.UUID;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TaskTrackerEmailSenderApplication {
    public static void main(String[] args) {
        if (System.getProperty("APP_INSTANCE_ID") == null) {
            System.setProperty("APP_INSTANCE_ID", UUID.randomUUID().toString());
        }

        SpringApplication.run(TaskTrackerEmailSenderApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}