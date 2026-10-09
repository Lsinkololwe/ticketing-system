package com.pml.shared.infrastructure.temporal;

import io.temporal.client.WorkflowClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@link TemporalGateway} wherever the Temporal starter has built a client.
 *
 * <p>Ordered after the starter's root-namespace configuration, which is what defines the
 * {@link WorkflowClient}; a service without Temporal on its classpath gets nothing.
 */
@AutoConfiguration(afterName = "io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration")
@ConditionalOnClass(WorkflowClient.class)
public class TemporalGatewayAutoConfiguration {

    @Bean
    @ConditionalOnBean(WorkflowClient.class)
    @ConditionalOnMissingBean
    public TemporalGateway temporalGateway(WorkflowClient client) {
        return new TemporalGateway(client);
    }
}
