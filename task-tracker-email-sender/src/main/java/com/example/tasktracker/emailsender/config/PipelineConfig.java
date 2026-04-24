package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.o11y.observation.annotation.ObservedExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class PipelineConfig {
    @Bean("virtualThreadExecutor")
    @ObservedExecutor(value = "email.sender.vthread.executor", metrics = false)
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}
