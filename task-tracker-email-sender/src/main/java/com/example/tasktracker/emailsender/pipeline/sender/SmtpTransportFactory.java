package com.example.tasktracker.emailsender.pipeline.sender;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.boot.autoconfigure.mail.MailProperties;

import java.util.Properties;

public class SmtpTransportFactory extends BasePooledObjectFactory<Transport> {

    private final MailProperties mailProperties;
    private final Properties javamailProperties;

    public SmtpTransportFactory(MailProperties mailProperties, Properties javamailProperties) {
        this.mailProperties = mailProperties;
        this.javamailProperties = javamailProperties;
    }

    @Override
    public Transport create() throws Exception {
        Session session = Session.getInstance(javamailProperties);
        Transport transport = session.getTransport(mailProperties.getProtocol());
        transport.connect(
                mailProperties.getHost(),
                mailProperties.getPort(),
                mailProperties.getUsername(),
                mailProperties.getPassword()
        );
        return transport;
    }

    @Override
    public PooledObject<Transport> wrap(Transport transport) {
        return new DefaultPooledObject<>(transport);
    }

    @Override
    public void destroyObject(PooledObject<Transport> p) throws Exception {
        p.getObject().close();
    }

    @Override
    public boolean validateObject(PooledObject<Transport> p) {
        return p.getObject().isConnected();
    }
}