package dev.rossetow.rollout.agent.config;

import dev.rossetow.rollout.agent.k8s.K8sTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Map;
import java.util.function.Function;

/**
 * Configuration for Spring AI Functions
 * Exposes all 8 K8s tools as Spring AI Functions that SAP AI Core can call
 */
@Configuration
public class K8sFunctionConfiguration {

    private final K8sTools k8sTools;

    public K8sFunctionConfiguration(K8sTools k8sTools) {
        this.k8sTools = k8sTools;
    }

    @Bean
    @Description("Debug a Kubernetes pod to get detailed information about its status and conditions")
    public Function<DebugPodRequest, Map<String, Object>> debugPod() {
        return request -> k8sTools.debugPod(request.namespace(), request.podName());
    }

    @Bean
    @Description("Get Kubernetes events for a namespace or specific pod")
    public Function<GetEventsRequest, Map<String, Object>> getEvents() {
        return request -> k8sTools.getEvents(request.namespace(), request.podName(), request.limit());
    }

    @Bean
    @Description("Get logs from a Kubernetes pod. Returns recent log entries including ERROR, CRITICAL, and ALERT messages if present.")
    public Function<GetLogsRequest, Map<String, Object>> getLogs() {
        return request -> k8sTools.getLogs(
            request.namespace(),
            request.podName(),
            request.containerName(),
            request.previous(),
            request.tailLines()
        );
    }
//
//    @Bean
//    @Description("Get resource metrics (CPU and memory usage) for a Kubernetes pod. IMPORTANT: You must provide both the namespace and the exact pod name.")
//    public Function<GetMetricsRequest, Map<String, Object>> getMetrics() {
//        return request -> k8sTools.getMetrics(request.namespace(), request.podName());
//    }

    @Bean
    @Description("Inspect Kubernetes resources in a namespace. Use labelSelector to filter pods by labels (e.g., 'role=stable' or 'role=canary')")
    public Function<InspectResourcesRequest, Map<String, Object>> inspectResources() {
        return request -> k8sTools.inspectResources(
            request.namespace(),
            request.resourceType(),
            request.resourceName(),
            request.labelSelector()
        );
    }

//    @Bean
//    @Description("Fetch application metrics from a pod's Prometheus metrics endpoint. Returns error rates, request counts, latency, and custom application metrics.")
//    public Function<FetchApplicationMetricsRequest, Map<String, Object>> fetchApplicationMetrics() {
//        return request -> k8sTools.fetchApplicationMetrics(
//            request.namespace(),
//            request.podName(),
//            request.metricsPath(),
//            request.port()
//        );
//    }

    @Bean
    @Description("Get canary diagnostics comparing stable and canary pods for an Argo Rollout. Fetches pod information and logs for both stable and canary deployments.")
    public Function<GetCanaryDiagnosticsRequest, Map<String, Object>> getCanaryDiagnostics() {
        return request -> k8sTools.getCanaryDiagnostics(
            request.namespace(),
            request.rolloutName(),
            request.containerName(),
            request.tailLines()
        );
    }

    // Request record classes for function parameters
    public record DebugPodRequest(String namespace, String podName) {}

    public record GetEventsRequest(String namespace, String podName, Integer limit) {}

    public record GetLogsRequest(
        String namespace,
        String podName,
        String containerName,
        Boolean previous,
        Integer tailLines
    ) {}

    public record GetMetricsRequest(String namespace, String podName) {}

    public record InspectResourcesRequest(
        String namespace,
        String resourceType,
        String resourceName,
        String labelSelector
    ) {}

    public record FetchApplicationMetricsRequest(
        String namespace,
        String podName,
        String metricsPath,
        Integer port
    ) {}

    public record GetCanaryDiagnosticsRequest(
        String namespace,
        String rolloutName,
        String containerName,
        Integer tailLines
    ) {}
}
