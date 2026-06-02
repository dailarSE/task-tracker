package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;

@Slf4j
public class PooledEmailTransport implements EmailTransport {

    private final GenericObjectPool<Transport> pool;
    private final SmtpEmailTransport delegate;
    private final EmailErrorResolver errorResolver;

    public PooledEmailTransport(SmtpEmailTransport delegate,
                                SmtpTransportFactory factory,
                                ReliabilityProperties properties,
                                EmailErrorResolver errorResolver) {
        this.delegate = delegate;
        this.errorResolver = errorResolver;

        int maxConnections = properties.getCapacity().getMaxActiveConnections();

        GenericObjectPoolConfig<Transport> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxConnections);
        config.setMaxIdle(maxConnections);
        config.setMinIdle(5);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);

        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofSeconds(2));

        this.pool = new GenericObjectPool<>(factory, config);
    }

    @Override
    public void send(SendInstructions instructions) {
        Transport transport = null;
        try {
            transport = pool.borrowObject();

            MimeMessage mimeMessage = delegate.createMimeMessage(instructions);
            mimeMessage.saveChanges();

            transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());

            pool.returnObject(transport);

        } catch (Exception e) {
            if (transport != null) {
                try {
                    pool.invalidateObject(transport);
                } catch (Exception ex) {
                    log.error("Failed to invalidate SMTP transport in pool", ex);
                }
            }
            throw errorResolver.resolve(e);
        }
    }
}