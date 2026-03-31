package com.example.tasktracker.emailsender.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private final ReliabilityProperties reliabilityProperties;
    private final MailProperties mailProperties;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        applyBaseProperties(mailSender);

        Properties props = mailSender.getJavaMailProperties();
        var network = reliabilityProperties.getNetworkLimit();

        String connectTimeout = String.valueOf(network.getConnectTimeout().toMillis());
        props.put("mail.smtp.connectiontimeout", connectTimeout);

        String socketTimeout = String.valueOf(network.getSocketReadTimeout().toMillis());
        props.put("mail.smtp.timeout", socketTimeout);
        props.put("mail.smtp.writetimeout", socketTimeout);

        return mailSender;
    }

    private void applyBaseProperties(JavaMailSenderImpl mailSender) {
        mailSender.setHost(mailProperties.getHost());
        mailSender.setPort(mailProperties.getPort());
        mailSender.setUsername(mailProperties.getUsername());
        mailSender.setPassword(mailProperties.getPassword());
        mailSender.setProtocol(mailProperties.getProtocol());
        if (mailProperties.getDefaultEncoding() != null) {
            mailSender.setDefaultEncoding(mailProperties.getDefaultEncoding().name());
        }

        mailSender.getJavaMailProperties().putAll(mailProperties.getProperties());
    }
}
