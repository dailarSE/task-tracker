package com.example.tasktracker.emailsender.o11y.observation.convention;

import io.micrometer.common.docs.KeyName;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RedisObservationTags {
    public enum LowCardinality implements KeyName {
        SYSTEM {
            @Override
            public String asString() {
                return "db.system.name";
            }
        },
        OPERATION {
            @Override
            public String asString() {
                return "db.operation.name";
            }
        },
        LOGICAL_OPERATION {
            @Override
            public String asString() {
                return "logical.operation.name";
            }
        },
        NAMESPACE {
            @Override
            public String asString() {
                return "db.namespace";
            }
        },
        PROCEDURE_NAME {
            @Override
            public String asString() {
                return "db.stored_procedure.name";
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

    public enum HighCardinality implements KeyName {
        BATCH_SIZE {
            @Override
            public String asString() {
                return "db.operation.batch.size";
            }
        }
    }
}
