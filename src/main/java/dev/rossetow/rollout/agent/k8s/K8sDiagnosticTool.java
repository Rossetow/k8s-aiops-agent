package dev.rossetow.rollout.agent.k8s;

import java.util.Map;
import java.util.function.Function;

/**
 * K8s Diagnostic Tool - Spring AI Tool for canary diagnostics
 * Provides the getCanaryDiagnostics tool for fetching stable and canary pod information
 */
public class K8sDiagnosticTool implements Function<K8sDiagnosticTool.Request, Map<String, Object>> {

    private final K8sTools k8sTools;

    public K8sDiagnosticTool(K8sTools k8sTools) {
        this.k8sTools = k8sTools;
    }

    public Map<String, Object> apply(Request request) {
        return k8sTools.getCanaryDiagnostics(
            request.namespace(),
            request.rolloutName(),
            request.containerName(),
            request.tailLines()
        );
    }

    /**
     * Request record for canary diagnostics
     */
    public record Request(
        String namespace,
        String rolloutName,
        String containerName,
        Integer tailLines
    ) {}
}
