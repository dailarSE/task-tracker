package com.example.tasktracker.emailsender.o11y.observation.context;

import io.micrometer.observation.Observation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RateLimitContext extends Observation.Context {
    private final String limiterId;

    public RateLimitContext(String limiterId) {
        this.limiterId = limiterId;
    }
}