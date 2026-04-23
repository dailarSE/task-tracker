package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.EmailSendContext;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;

public class EmailSmtpConvention extends BaseO11yConvention<EmailSendContext> {

    @Override
    public KeyValues getLowCardinalityKeyValues(EmailSendContext context) {
        return super.getLowCardinalityKeyValues(context).and(
                LowCardinalityTags.FROM_ADDRESS.asString(), context.getFromAddress(),
                LowCardinalityTags.TO_DOMAIN.asString(), context.getToDomain(),
                LowCardinalityTags.TEMPLATE_ID.asString(), context.getTemplateId(),
                LowCardinalityTags.CONTENT_TYPE.asString(), context.getContentType(),
                LowCardinalityTags.PEER_SERVICE.asString(), context.getRemoteServiceName(),
                LowCardinalityTags.SERVER_ADDRESS.asString(), context.getRemoteHost(),
                LowCardinalityTags.SERVER_PORT.asString(), context.getRemotePort()
        );
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(EmailSendContext context) {
        return super.getHighCardinalityKeyValues(context).
                and(HighCardinalityTags.CORRELATION_ID.asString(), context.getCorrelationId());
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context.getClass() == EmailSendContext.class;
    }

    @Override
    public String getName() {
        return "email.send.duration";
    }

    @Override
    public String getContextualName(EmailSendContext context) {
        return "email.send";
    }

    public enum LowCardinalityTags implements KeyName {
        FROM_ADDRESS {
            @Override
            public String asString() {
                return "email.from.address";
            }
        },
        TO_DOMAIN {
            @Override
            public String asString() {
                return "email.to.domain";
            }
        },
        TEMPLATE_ID {
            @Override
            public String asString() {
                return "email.template.id";
            }
        },
        CONTENT_TYPE {
            @Override
            public String asString() {
                return "email.content_type";
            }
        },
        PEER_SERVICE {
            @Override
            public String asString() {
                return "peer.service";
            }
        },
        SERVER_ADDRESS {
            @Override
            public String asString() {
                return "server.address";
            }
        },
        SERVER_PORT {
            @Override
            public String asString() {
                return "server.port";
            }
        }
    }

    public enum HighCardinalityTags implements KeyName {
        CORRELATION_ID {
            @Override
            public String asString() {
                return "email.correlation_id";
            }
        }
    }
}
