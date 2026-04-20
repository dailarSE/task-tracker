package com.example.tasktracker.emailsender.o11y.observation.convention;

import io.micrometer.common.docs.KeyName;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KafkaObservationTags {
    public enum LowCardinality implements KeyName {

        SYSTEM {
            @Override
            public String asString() {
                return "messaging.system";
            }
        },
        OP_TYPE {
            @Override
            public String asString() {
                return "messaging.operation.type";
            }
        },
        OP_NAME {
            @Override
            public String asString() {
                return "messaging.operation.name";
            }
        },
        DESTINATION {
            @Override
            public String asString() {
                return "messaging.destination.name";
            }
        },
        CONSUMER_GROUP {
            @Override
            public String asString() {
                return "messaging.consumer.group.name";
            }
        },
        CLIENT_ID {
            @Override
            public String asString() {
                return "messaging.client.id";
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
        },
        BATCH_SIZE {
            @Override
            public String asString() {
                return "messaging.batch.message_count";
            }
        }
    }

    public enum HighCardinality implements KeyName {
        MESSAGE_ID {
            @Override
            public String asString() {
                return "messaging.message.id";
            }
        },
        CONVERSATION_ID {
            @Override
            public String asString() {
                return "messaging.message.conversation_id";
            }
        },
        OFFSET {
            @Override
            public String asString() {
                return "messaging.kafka.offset";
            }
        },
        PARTITION {
            @Override
            public String asString() {
                return "messaging.destination.partition.id";
            }
        },
        BODY_SIZE {
            @Override
            public String asString() {
                return "messaging.message.body.size";
            }
        },
        ENVELOPE_SIZE {
            @Override
            public String asString() {
                return "messaging.message.envelope.size";
            }
        }
    }
}
