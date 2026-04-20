package com.example.tasktracker.emailsender.o11y.observation.convention;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class MessagingOperation {
    public static final class Type {
        public static final String CREATE = "create";
        public static final String PROCESS = "process";
        public static final String RECEIVE = "receive";
        public static final String SEND = "send";
        public static final String SETTLE = "settle";
    }

    public static final class Name {
        public static final String PUBLISH = "publish";
        public static final String POLL = "poll";
        public static final String PROCESS = "process";
    }
}
