package com.example.tasktracker.emailsender.o11y.config;

import com.example.tasktracker.emailsender.o11y.kafka.ObservedKafkaTemplate;
import com.example.tasktracker.emailsender.o11y.observation.context.KafkaContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordPublishContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaPublishConvention;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@ConditionalOnProperty(value = "app.observability.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class KafkaObservabilityEnhancer implements BeanPostProcessor {

    private final ObjectProvider<ObservationRegistry> registry;
    private final ObjectProvider<KafkaContextFactory> contextFactory;
    private final ObjectProvider<KafkaPublishConvention<KafkaRecordPublishContext>> kafkaPublishConvention;
    private final ConfigurableListableBeanFactory beanFactory;

    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        if (bean instanceof KafkaTemplate<?, ?> originTemplate && !(bean instanceof ObservedKafkaTemplate)) {

            if (!beanFactory.containsBeanDefinition(beanName)) {
                log.debug("O11Y: Skipping KafkaTemplate [{}] - no BeanDefinition found (manually registered?).", beanName);
                return bean;
            }

            ResolvableType declaredType = beanFactory.getMergedBeanDefinition(beanName).getResolvableType();
            ResolvableType templateType = declaredType.as(KafkaTemplate.class);
            Class<?> keyType = templateType.resolveGeneric(0);
            Class<?> valueType = templateType.resolveGeneric(1);

            if (keyType == byte[].class && valueType == byte[].class) {
                return wrapWithObserved((KafkaTemplate<byte[], byte[]>) originTemplate);
            } else {
                log.debug("O11Y: Skipping KafkaTemplate [{}] (incompatible types: {}/{}).", beanName, keyType, valueType);
            }
        }

        if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
            factory.getContainerProperties().setObservationEnabled(false);
            factory.getContainerProperties().setMicrometerEnabled(false);
        }

        return bean;
    }

    private ObservedKafkaTemplate wrapWithObserved(KafkaTemplate<byte[], byte[]> origin) {
        ObservedKafkaTemplate observed = new ObservedKafkaTemplate(
                origin.getProducerFactory(),
                registry.getIfAvailable(),
                contextFactory.getIfAvailable(),
                kafkaPublishConvention.getIfAvailable()
        );

        observed.setDefaultTopic(origin.getDefaultTopic());
        observed.setMessageConverter(origin.getMessageConverter());

        return observed;
    }
}
