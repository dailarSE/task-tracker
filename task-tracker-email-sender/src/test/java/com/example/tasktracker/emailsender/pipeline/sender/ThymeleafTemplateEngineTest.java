package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.IThrottledTemplateProcessor;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.IContext;

import java.io.Writer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ThymeleafTemplateEngineTest {

    private TemplateEngineStub thymeleafStub;
    private MessageSourceStub messageSourceStub;
    private ThymeleafTemplateEngine engine;

    @BeforeEach
    void setUp() {
        thymeleafStub = new TemplateEngineStub();
        messageSourceStub = new MessageSourceStub();
        engine = new ThymeleafTemplateEngine(thymeleafStub, messageSourceStub);
    }

    @Test
    @DisplayName("Happy Path: Should render subject and body with requested locale")
    void shouldRenderSuccessfullyWithProvidedLocale() {
        // Given
        String template = "USER_WELCOME";
        Locale frenchLocale = Locale.FRANCE;
        messageSourceStub.setResponse(
                "email.subject." + template, frenchLocale, "Bienvenue à bord !");
        thymeleafStub.setResponseBody("<html><body>Bonjour!</body></html>");

        // When
        var result = engine.process(template, Map.of("user", "Jean"), "fr-FR");

        // Then
        assertEquals("Bienvenue à bord !", result.subject());
        assertEquals("<html><body>Bonjour!</body></html>", result.body());

        assertEquals(template, thymeleafStub.capturedTemplate);
        assertEquals(frenchLocale, thymeleafStub.capturedLocale);
    }

    @Test
    @DisplayName("Locale Fallback: Should use default (EN) locale for subject AND body if requested locale's subject is missing")
    void shouldFallbackToDefaultLocaleForBothSubjectAndBody() {
        // Given
        String template = "USER_WELCOME";
        // Настраиваем отсутствие FR и наличие EN
        messageSourceStub.setResponse(
                "email.subject." + template, EmailTemplateEngine.DEFAULT_LOCALE, "Welcome Aboard!");

        // When
        var result = engine.process(template, Map.of(), "fr-FR");

        // Then
        assertEquals("Welcome Aboard!", result.subject());
        assertEquals(EmailTemplateEngine.DEFAULT_LOCALE, thymeleafStub.capturedLocale);
    }

    @Test
    @DisplayName("Configuration Error: Should throw FatalProcessingException if subject is missing even in default locale")
    void shouldThrowFatalExceptionWhenSubjectIsMissingInAllLocales() {
        // Given: messageSourceStub по умолчанию кидает NoSuchMessageException

        // When & Then
        var ex = assertThrows(FatalProcessingException.class,
                () -> engine.process("UNKNOWN_TEMPLATE", Map.of(), "en-US"));

        assertEquals(RejectReason.INTERNAL_ERROR, ex.getRejectReason());
        assertInstanceOf(NoSuchMessageException.class, ex.getCause());
    }

    // --- Помощники-заглушки (Stubs) ---

    private static class TemplateEngineStub implements ITemplateEngine {
        String capturedTemplate;
        Locale capturedLocale;
        String responseBody = "default body";

        void setResponseBody(String body) { this.responseBody = body; }

        @Override
        public String process(String template, IContext context) {
            this.capturedTemplate = template;
            this.capturedLocale = context.getLocale();
            return responseBody;
        }

        // Заглушки для неиспользуемых методов интерфейса
        @Override public IEngineConfiguration getConfiguration() { return null; }
        @Override public String process(String t, Set<String> s, IContext c) { return null; }
        @Override public String process(TemplateSpec s, IContext c) { return null; }
        @Override public void process(String t, IContext c, Writer w) {}
        @Override public void process(String t, Set<String> s, IContext c, Writer w) {}
        @Override public void process(TemplateSpec s, IContext c, Writer w) {}
        @Override public IThrottledTemplateProcessor processThrottled(String t, IContext c) { return null; }
        @Override public IThrottledTemplateProcessor processThrottled(String t, Set<String> s, IContext c) { return null; }
        @Override public IThrottledTemplateProcessor processThrottled(TemplateSpec s, IContext c) { return null; }
    }

    private static class MessageSourceStub implements MessageSource {
        private final AtomicReference<String> expectedCode = new AtomicReference<>();
        private final AtomicReference<Locale> expectedLocale = new AtomicReference<>();
        private final AtomicReference<String> response = new AtomicReference<>();

        void setResponse(String code, Locale locale, String text) {
            this.expectedCode.set(code);
            this.expectedLocale.set(locale);
            this.response.set(text);
        }

        @Override
        public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
            if (code.equals(expectedCode.get()) && locale.equals(expectedLocale.get())) {
                return response.get();
            }
            throw new NoSuchMessageException("No message found for " + code + " and " + locale);
        }

        @Override public String getMessage(String c, Object[] a, String d, Locale l) { return null; }
        @Override public String getMessage(MessageSourceResolvable r, Locale l) { return null; }
    }
}