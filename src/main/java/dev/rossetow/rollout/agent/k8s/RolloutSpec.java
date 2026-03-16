package dev.rossetow.rollout.agent.k8s;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.kubernetes.api.model.LabelSelector;

/**
 * Argo Rollouts Spec
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RolloutSpec {
    
    private LabelSelector selector;
    
    public LabelSelector getSelector() {
        return selector;
    }
    
    public void setSelector(LabelSelector selector) {
        this.selector = selector;
    }
}