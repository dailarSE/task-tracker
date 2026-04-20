package com.example.tasktracker.emailsender.o11y.observation.util;

import com.example.tasktracker.emailsender.infra.RuntimeInstanceIdProvider;
import lombok.Getter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * мусорный класс-костыль.
 */
@Component
public class KafkaPropertiesResolver {

    @Getter
    private final String clientId;

    @Getter
    private final String consumerGroup;

    @Getter
    private final String serverAddress;

    @Getter
    private final int serverPort;

    public KafkaPropertiesResolver(
            KafkaProperties kafkaProperties,
            ObjectProvider<KafkaConnectionDetails> connectionDetailsProvider,
            RuntimeInstanceIdProvider instanceIdProvider,
            @Value("${spring.application.name}") String appName) {

        this.clientId = determineClientId(kafkaProperties, appName, instanceIdProvider.getInstanceId());
        this.consumerGroup = determineConsumerGroup(kafkaProperties);

        KafkaConnectionDetails details = connectionDetailsProvider.getIfAvailable();
        List<String> bootstrapServers = (details != null)
                ? details.getBootstrapServers()
                : kafkaProperties.getBootstrapServers();

        ServerEndpoint endpoint = determineServerEndpoint(bootstrapServers);
        this.serverAddress = endpoint.host();
        this.serverPort = endpoint.port();
    }

    private String determineClientId(KafkaProperties props, String appName, String instanceId) {
        if (StringUtils.hasText(props.getConsumer().getClientId())) {
            return props.getConsumer().getClientId();
        }
        if (StringUtils.hasText(props.getClientId())) {
            return props.getClientId();
        }
        return appName + "@" + instanceId;
    }

    private String determineConsumerGroup(KafkaProperties props) {
        if (StringUtils.hasText(props.getConsumer().getGroupId())) {
            return props.getConsumer().getGroupId();
        }
        return "default-consumer-group";
    }

    /**
     * Парсит список bootstrap серверов.
     * Если их несколько, объединяет хосты через запятую для OTel SemConv.
     */
    private ServerEndpoint determineServerEndpoint(@NonNull List<String> bootstrapServers) {
        List<String> servers = bootstrapServers.stream().sorted().toList();

        if (servers.size() == 1) {
            return parseEndpoint(servers.getFirst());
        }

        // OTel SemConv: server.address = "kafka1,kafka2,kafka3", server.port = 9092 (если порты одинаковые)
        StringBuilder hostBuilder = new StringBuilder();
        int commonPort = -1;
        boolean multiPort = false;

        for (int i = 0; i < servers.size(); i++) {
            ServerEndpoint ep = parseEndpoint(servers.get(i));
            hostBuilder.append(ep.host());
            if (i < servers.size() - 1) {
                hostBuilder.append(",");
            }

            if (commonPort == -1) {
                commonPort = ep.port();
            } else if (commonPort != ep.port()) {
                multiPort = true;
            }
        }

        // Если в кластере брокеры висят на разных портах, порт не указываем
        return new ServerEndpoint(hostBuilder.toString(), multiPort ? -1 : commonPort);
    }

    private ServerEndpoint parseEndpoint(String server) {
        if (!server.contains(":")) {
            return new ServerEndpoint(server, 9092);
        }

        // IPv6: [2001:db8::1]:9092
        int lastColon = server.lastIndexOf(':');
        String host = server.substring(0, lastColon).replace("[", "").replace("]", "");
        int port;
        try {
            port = Integer.parseInt(server.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            port = 9092;
        }

        return new ServerEndpoint(host, port);
    }

    private record ServerEndpoint(String host, int port) {}
}
