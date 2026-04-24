package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.AssemblyContext;
import io.micrometer.observation.Observation;

public class AssemblyConvention extends BaseO11yConvention<AssemblyContext> {

    public enum Names {
        TIMER_NAME("email_sender.batch.assembly.duration"),
        SPAN_NAME("batch assembly");

        private final String value;

        Names(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    @Override
    public String getName() {
        return Names.TIMER_NAME.value();
    }

    @Override
    public String getContextualName(AssemblyContext context) {
        return Names.SPAN_NAME.value();
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof AssemblyContext;
    }
}