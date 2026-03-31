package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;

import java.util.concurrent.CompletableFuture;

public interface EmailClient {
    CompletableFuture<Void> send(TriggerCommand sendCommand);
}
