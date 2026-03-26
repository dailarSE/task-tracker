package com.example.tasktracker.emailsender.pipeline.ratelimit;

public interface RpsLimiter {
    int acquire(int requestedAmount) throws InterruptedException;
}
