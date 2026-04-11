package com.example.tasktracker.emailsender.o11y.config;

import com.example.tasktracker.emailsender.o11y.observation.annotation.ObservedExecutor;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.MethodMetadata;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

@Configuration
@ConditionalOnProperty(value = "app.observability.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ExecutorObservabilityEnabler implements BeanPostProcessor {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<ContextSnapshotFactory> contextSnapshotFactoryProvider;

    private final ConfigurableListableBeanFactory beanFactory;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ExecutorService executor) {
            ObservedExecutor ann = findAnnotation(beanName);
            if (ann != null) {
                if (ann.propagation()) {
                    executor = ContextExecutorService.wrap(executor,
                            Objects.requireNonNull(contextSnapshotFactoryProvider.getIfAvailable()));
                }
                if (ann.metrics()) {
                    executor = ExecutorServiceMetrics.monitor(Objects.requireNonNull(meterRegistryProvider.getIfAvailable()),
                            executor, ann.value(), Tags.empty());
                }
                return executor;
            }
        }
        return bean;
    }

    private ObservedExecutor findAnnotation(String beanName) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return null;
        }
        BeanDefinition bd = beanFactory.getMergedBeanDefinition(beanName);

        if (bd instanceof AnnotatedBeanDefinition abd) {
            MethodMetadata factoryMethodMetadata = abd.getFactoryMethodMetadata();
            if (factoryMethodMetadata != null) {
                var mergedAnn = factoryMethodMetadata.getAnnotations().get(ObservedExecutor.class);
                if (mergedAnn.isPresent()) {
                    return mergedAnn.synthesize();
                }
            }
        }

        return beanFactory.findAnnotationOnBean(beanName, ObservedExecutor.class);
    }
}