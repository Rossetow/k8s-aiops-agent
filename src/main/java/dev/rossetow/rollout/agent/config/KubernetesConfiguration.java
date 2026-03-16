package dev.rossetow.rollout.agent.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Kubernetes Client
 */
@Configuration
public class KubernetesConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KubernetesConfiguration.class);

    @Bean
    public KubernetesClient kubernetesClient() {
        log.info("Initializing Kubernetes client");
        
        try {
            // Try to create client with default configuration (in-cluster or kubeconfig)
            Config config = new ConfigBuilder().build();
            KubernetesClient client = new KubernetesClientBuilder()
                .withConfig(config)
                .build();
            
            log.info("Kubernetes client initialized successfully");
            log.info("Master URL: {}", config.getMasterUrl());
            log.info("Namespace: {}", config.getNamespace());
            
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize Kubernetes client", e);
            throw new RuntimeException("Failed to initialize Kubernetes client", e);
        }
    }
}