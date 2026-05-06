package com.example.tasktracker.emailsender.util;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;

import java.util.Map;

public class TestDataFactory {
    public static TriggerCommand welcome(String cid, Long userId) {
        return new TriggerCommand("test@mail.ru", "USER_WELCOME", Map.of(), "ru", userId, cid);
    }

    public static TriggerCommand toDlt(String cid, Long userId) {
        return new TriggerCommand("", "USER_WELCOME", Map.of(), "en", userId, cid);
    }
}
