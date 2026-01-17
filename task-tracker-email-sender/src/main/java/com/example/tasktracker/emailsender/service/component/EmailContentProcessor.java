package com.example.tasktracker.emailsender.service.component;

import com.example.tasktracker.emailsender.service.model.PreparedEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailContentProcessor {

    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    /**
     * Формирует тему и тело письма.
     * Обрабатывает ошибки конфигурации (отсутствие шаблонов, ключей).
     * Реализует fallback локали.
     */
    public PreparedEmail process(String templateName, Map<String, Object> templateContext, Locale requestedLocale) {
        Locale finalLocale = resolveLocaleAndCheckSubjectKey(templateName, requestedLocale);
        String subject = getSubject(templateName, finalLocale);
        String body = renderBody(templateName, templateContext, finalLocale);

        return new PreparedEmail(subject, body, true);
    }

    private Locale resolveLocaleAndCheckSubjectKey(String templateName, Locale requestedLocale) {
        String subjectKey = getSubjectKey(templateName);
        try {
            // Проверяем наличие темы для запрошенной локали
            messageSource.getMessage(subjectKey, null, requestedLocale);
            return requestedLocale;
        } catch (NoSuchMessageException e) {
            log.warn("Subject key '{}' not found for locale '{}'. Falling back to default '{}'.",
                    subjectKey, requestedLocale, DEFAULT_LOCALE);
            try {
                // Проверяем наличие темы для дефолтной локали
                messageSource.getMessage(subjectKey, null, DEFAULT_LOCALE);
                return DEFAULT_LOCALE;
            } catch (NoSuchMessageException ex) {
                log.error("CRITICAL CONFIGURATION ERROR: Subject key '{}' not found even for default locale.",
                        subjectKey);
                throw new MailPreparationException("Could not find subject for email template: " + templateName, ex);
            }
        }
    }

    private String getSubject(String templateName, Locale locale) {
        // Ключ есть, так как проверили это в resolveLocaleAndCheckSubjectKey
        return messageSource.getMessage(getSubjectKey(templateName), null, locale);
    }

    private String renderBody(String templateName, Map<String, Object> templateContext, Locale locale) {
        try {
            Context context = new Context(locale);
            context.setVariables(templateContext);
            return templateEngine.process(templateName, context);
        } catch (TemplateEngineException e) {
            log.error("CRITICAL CONFIGURATION ERROR: Template rendering failed for '{}'.", templateName, e);
            throw new MailPreparationException("Failed to render email template: " + templateName, e);
        }
    }

    private String getSubjectKey(String templateName) {
        return "email.subject." + templateName;
    }
}