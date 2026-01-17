package com.example.tasktracker.emailsender.service;

import com.example.tasktracker.emailsender.service.component.EmailContentProcessor;
import com.example.tasktracker.emailsender.service.component.SmtpEmailSender;
import com.example.tasktracker.emailsender.service.model.PreparedEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final EmailContentProcessor contentProcessor;
    private final SmtpEmailSender emailSender;

    public void sendHtmlEmail(String to, String templateName, Map<String, Object> templateContext, Locale locale) {
        PreparedEmail content = contentProcessor.process(templateName, templateContext, locale);
        emailSender.send(to, content);
    }
}