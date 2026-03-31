package com.example.tasktracker.emailsender.config.init;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

public class ConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<InconsistentConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InconsistentConfigurationException cause) {
        return new FailureAnalysis(
                "Config Inconsistency: " + cause.getMessage(),
                cause.getAction(),
                cause
        );
    }
}
