package com.example.tasktracker.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskTrackerSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskTrackerSchedulerApplication.class, args);
    }
}