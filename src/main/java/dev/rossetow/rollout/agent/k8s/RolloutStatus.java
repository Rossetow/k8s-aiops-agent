package dev.rossetow.rollout.agent.k8s;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Argo Rollouts Status
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RolloutStatus {
    // We don't need to parse status fields for our use case
}