package com.example.tasktracker.emailsender.o11y.observation.context;

import com.example.tasktracker.emailsender.o11y.observation.util.RedisPropertiesResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisContextFactory {

    private final RedisPropertiesResolver redisPropertiesResolver;

    /**
     * Создает контекст для Redis операции.
     *
     * @param operationName Имя команды (EVAL, SET, etc)
     * @param scriptSha1    sha1 скрипта (для db.stored_procedure.name)
     * @param batchSize     Размер батча (для db.operation.batch.size)
     */
    public RedisObservationContext createContext(String operationName,
                                                 String logicalOperationName,
                                                 Integer batchSize,
                                                 String scriptSha1) {
        RedisObservationContext context = new RedisObservationContext(
                operationName,
                logicalOperationName,
                redisPropertiesResolver.getAddress(),
                redisPropertiesResolver.getPort(),
                redisPropertiesResolver.getDatabaseIndex()
        );

        context.setRemoteServiceName("redis");

        if (batchSize != null) context.setBatchSize(batchSize);
        if (scriptSha1 != null) context.setScriptSha1(scriptSha1);

        return context;
    }
}
