package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ThymeleafTemplateEngine implements EmailTemplateEngine {

    private final ITemplateEngine templateEngine;
    private final MessageSource messageSource;
    private static final String SUBJECT_KEY_PREFIX = "email.subject.";


    /**
     * Формирует тему и тело письма.
     * Обрабатывает ошибки конфигурации (отсутствие шаблонов, ключей).
     * Реализует fallback локали.
     */
    public RenderingResult process(String templateName, Map<String, Object> templateContext, String localeTag) {
        Locale finalLocale = resolveLocaleAndCheckSubjectKey(templateName, parseLocale(localeTag));
        String subject = getSubject(templateName, finalLocale);
        String body = renderBody(templateName, templateContext, finalLocale);

        return new RenderingResult(subject, body);
    }

    private Locale resolveLocaleAndCheckSubjectKey(String templateName, Locale requestedLocale) {
        String subjectKey = getSubjectKey(templateName);
        try {
            messageSource.getMessage(subjectKey, null, requestedLocale);
            return requestedLocale;
        } catch (NoSuchMessageException e) {
            log.warn("Subject key '{}' not found for localeTag '{}'. Falling back to default '{}'.",
                    subjectKey, requestedLocale, DEFAULT_LOCALE);
            try {
                messageSource.getMessage(subjectKey, null, DEFAULT_LOCALE);
                return DEFAULT_LOCALE;
            } catch (NoSuchMessageException ex) {
                log.error("CRITICAL CONFIGURATION ERROR: Subject key '{}' not found even for default localeTag.",
                        subjectKey);
                throw new FatalProcessingException(RejectReason.INTERNAL_ERROR,
                        "Could not find subject for email template: " + templateName,
                        ex);
            }
        }
    }

    private String renderBody(String templateName, Map<String, Object> templateContext, Locale locale) {
        try {
            Context context = new Context(locale);
            context.setVariables(templateContext);
            return templateEngine.process(templateName, context);
        } catch (TemplateEngineException e) {
            log.error("CRITICAL CONFIGURATION ERROR: Template rendering failed for '{}'.", templateName, e);
            throw new FatalProcessingException(RejectReason.INTERNAL_ERROR,
                    "Failed to render email template: " + templateName,
                    e);
        }
    }

    private Locale parseLocale(String tag) {
        if (!StringUtils.hasText(tag)) {
            return DEFAULT_LOCALE;
        }

        Locale requested = Locale.forLanguageTag(tag);
        return requested.getLanguage().isEmpty() ? DEFAULT_LOCALE : requested;
    }

    private String getSubject(String templateName, Locale locale) {
        // Ключ есть, так как проверили это в resolveLocaleAndCheckSubjectKey
        return messageSource.getMessage(getSubjectKey(templateName), null, locale);
    }

    private String getSubjectKey(String templateName) {
        return SUBJECT_KEY_PREFIX + templateName;
    }
}