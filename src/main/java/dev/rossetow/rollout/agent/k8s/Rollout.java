package dev.rossetow.rollout.agent.k8s;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * Argo Rollouts Custom Resource Definition
 */
@Group("argoproj.io")
@Version("v1alpha1")
public class Rollout extends CustomResource<RolloutSpec, RolloutStatus> implements Namespaced {
    
    @Override
    protected RolloutSpec initSpec() {
        return new RolloutSpec();
    }
    
    @Override
    protected RolloutStatus initStatus() {
        return new RolloutStatus();
    }
}