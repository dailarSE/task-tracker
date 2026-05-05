package com.example.tasktracker.emailsender.util;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({KafkaSupport.class, RedisSupport.class, EmailSupport.class})
public class TestSupportConfig {}
