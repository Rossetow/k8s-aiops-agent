package dev.rossetow.rollout.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Application for Kubernetes AI Agent
 * Migrated from Quarkus to Spring Boot with SAP AI Core integration
 */
@SpringBootApplication
public class KubernetesAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(KubernetesAgentApplication.class, args);
    }
}