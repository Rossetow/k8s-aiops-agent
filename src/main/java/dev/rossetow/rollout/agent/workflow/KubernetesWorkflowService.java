package dev.rossetow.rollout.agent.workflow;

import dev.rossetow.rollout.agent.agents.AnalysisAgentService;
import dev.rossetow.rollout.agent.agents.DiagnosticAgentService;
import dev.rossetow.rollout.agent.agents.RemediationAgentService;
import dev.rossetow.rollout.agent.agents.ScoringAgentService;
import dev.rossetow.rollout.agent.model.AnalysisResult;
import dev.rossetow.rollout.agent.model.ScoringResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Kubernetes Workflow Service - Migrated from LangChain4j to Spring AI
 * Orchestrates the complete analysis workflow: Diagnostics → Analysis (with retry) → Remediation
 */
@Service
public class KubernetesWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesWorkflowService.class);
    private static final int MAX_ANALYSIS_ITERATIONS = 3;

    private final DiagnosticAgentService diagnosticAgent;
    private final AnalysisAgentService analysisAgent;
    private final ScoringAgentService scoringAgent;
    private final RemediationAgentService remediationAgent;

    public KubernetesWorkflowService(
            DiagnosticAgentService diagnosticAgent,
            AnalysisAgentService analysisAgent,
            ScoringAgentService scoringAgent,
            RemediationAgentService remediationAgent
    ) {
        this.diagnosticAgent = diagnosticAgent;
        this.analysisAgent = analysisAgent;
        this.scoringAgent = scoringAgent;
        this.remediationAgent = remediationAgent;
    }

    /**
     * Execute the complete Kubernetes analysis workflow
     * 
     * @param message User message describing the analysis request
     * @param repoUrl Repository URL for remediation (optional)
     * @param baseBranch Base branch for remediation (optional)
     * @return Final analysis result with remediation status
     */
    public AnalysisResult execute(String message, String repoUrl, String baseBranch) {
        log.info("=== Starting Kubernetes Workflow ===");
        log.info("Message: {}", message);
        log.info("Repository: {}", repoUrl != null ? repoUrl : "none");
        
        try {
            // Step 1: Gather diagnostics
            log.info("Step 1: Gathering diagnostics");
            String diagnosticData = diagnosticAgent.gatherDiagnostics(message);
            
            // Step 2: Analyze with retry loop
            log.info("Step 2: Analyzing diagnostics (with retry up to {} times)", MAX_ANALYSIS_ITERATIONS);
            AnalysisResult analysisResult = analyzeWithRetry(diagnosticData);
            
            // Step 3: Implement remediation if needed
            if (repoUrl != null && !repoUrl.isEmpty()) {
                log.info("Step 3: Implementing remediation");
                analysisResult = remediationAgent.implementRemediation(
                    diagnosticData,
                    analysisResult,
                    repoUrl,
                    baseBranch
                );
            } else {
                log.info("Step 3: Skipping remediation (no repository URL provided)");
            }
            
            log.info("=== Workflow Complete ===");
            log.info("Promote: {}, Confidence: {}%, PR/Issue: {}",
                analysisResult.promote(),
                analysisResult.confidence(),
                analysisResult.prLink() != null ? analysisResult.prLink() : "none");
            
            return analysisResult;
            
        } catch (Exception e) {
            log.error("Error in workflow execution", e);
            // Return a safe error result
            return new AnalysisResult(
                false,
                0,
                "Workflow failed: " + e.getMessage(),
                "Error in analysis workflow",
                "Manual investigation required",
                null,
                repoUrl,
                baseBranch
            );
        }
    }

    /**
     * Analyze with retry loop - attempts analysis up to MAX_ANALYSIS_ITERATIONS times
     * Retries if scoring indicates low confidence
     * 
     * @param diagnosticData Diagnostic data from the diagnostic agent
     * @return Final analysis result (either passing score or last attempt)
     */
    private AnalysisResult analyzeWithRetry(String diagnosticData) {
        AnalysisResult analysisResult = null;
        ScoringResult scoringResult = null;
        
        for (int iteration = 1; iteration <= MAX_ANALYSIS_ITERATIONS; iteration++) {
            log.info("Analysis attempt {}/{}", iteration, MAX_ANALYSIS_ITERATIONS);
            
            // Run analysis
            analysisResult = analysisAgent.analyze(diagnosticData);
            
            // Score the analysis
            scoringResult = scoringAgent.evaluate(analysisResult);
            
            log.info("Score: {}, Needs Retry: {}, Reason: {}",
                scoringResult.score(),
                scoringResult.needsRetry(),
                scoringResult.reason());
            
            // Exit if score is good enough
            if (!scoringResult.needsRetry()) {
                log.info("Analysis passed quality threshold on attempt {}", iteration);
                break;
            }
            
            // If this is the last iteration, we'll use this result anyway
            if (iteration == MAX_ANALYSIS_ITERATIONS) {
                log.warn("Max iterations reached, using last analysis result");
            }
        }
        
        return analysisResult;
    }
}