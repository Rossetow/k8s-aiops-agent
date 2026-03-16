package dev.rossetow.rollout.agent.controller;

import dev.rossetow.rollout.agent.model.AnalysisResult;
import dev.rossetow.rollout.agent.model.KubernetesAgentRequest;
import dev.rossetow.rollout.agent.model.KubernetesAgentResponse;
import dev.rossetow.rollout.agent.workflow.KubernetesWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Kubernetes Agent
 * Provides endpoints for analyzing Kubernetes issues and creating remediation PRs
 */
@RestController
@RequestMapping()
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final KubernetesWorkflowService workflowService;

    public AgentController(KubernetesWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/")
    public ResponseEntity<HealthResponse> ping() {
        return ResponseEntity.ok(new HealthResponse(
                "pong",
                "KubernetesAgent",
                "1.0.1"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(
            "healthy",
            "KubernetesAgent",
            "1.0.1"
        ));
    }

    /**
     * Main analysis endpoint
     * 
     * @param request Analysis request containing user prompt and optional context
     * @return Analysis result with recommendations and optional PR link
     */
    @PostMapping("/a2a/analyze")
    public ResponseEntity<KubernetesAgentResponse> analyze(@RequestBody KubernetesAgentRequest request) {
        log.info("Received analysis request from user: {}", request.userId());
        log.info("Prompt: {}", request.prompt());
        
        try {
            // Extract repository info from context if available
            String repoUrl = request.context() != null ? 
                (String) request.context().get("repoUrl") : null;
            String baseBranch = request.context() != null ? 
                (String) request.context().get("baseBranch") : "main";
            
            // Execute the workflow
            AnalysisResult result = workflowService.execute(
                request.prompt(),
                repoUrl,
                baseBranch
            );
            
            // Convert to response format
            KubernetesAgentResponse response = new KubernetesAgentResponse(
                result.analysis(),
                result.rootCause(),
                result.remediation(),
                result.prLink(),
                result.promote(),
                result.confidence()
            );
            
            log.info("Analysis complete: promote={}, confidence={}%", 
                result.promote(), result.confidence());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing analysis request", e);
            
            // Return error response
            KubernetesAgentResponse errorResponse = new KubernetesAgentResponse(
                "Analysis failed: " + e.getMessage(),
                "Error in analysis workflow",
                "Manual investigation required",
                null,
                false,
                0
            );
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Health check response record
     */
    public record HealthResponse(
        String status,
        String agent,
        String version
    ) {}
}