package dev.rossetow.rollout.agent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.ai.sdk.orchestration.OrchestrationModuleConfig;
import com.sap.ai.sdk.orchestration.spring.OrchestrationChatOptions;
import dev.rossetow.rollout.agent.k8s.K8sTools;
import dev.rossetow.rollout.agent.model.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.sap.ai.sdk.orchestration.OrchestrationAiModel.GPT_5;

/**
 * Analysis Agent Service - Migrated from LangChain4j to Spring AI
 * Analyzes Kubernetes diagnostic data and application metrics
 */
@Service
public class AnalysisAgentService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAgentService.class);

    private final ObjectMapper objectMapper;
    private final K8sTools k8sTools;
    private final ChatClient chatClient;

    private static final String SYSTEM_MESSAGE = """
        /no_think
        
        BE CONCISE. NO verbose reasoning. Fast K8s SRE analysis.
        
        PRIORITY: Metrics > Logs > Events
        
        THRESHOLDS:
        - Error rate: canary ≤ stable + 5%
        - Success rate: canary ≥ 80%
        - p95 latency: canary ≤ stable * 1.5
        - p99 latency: canary ≤ stable * 2.0
        - Min requests: ≥ 50
        
        DO NOT PROMOTE if:
        - Canary error rate > stable + 5%
        - Success rate < 80%
        - p95 > stable * 1.5
        - CRITICAL ERROR in logs
        - Crash loops
        
        PROMOTE if metrics good + no critical errors + sufficient data.
        
        JSON OUTPUT:
        {
          "promote": true/false,
          "confidence": 0-100,
          "analysis": "brief comparison",
          "rootCause": "issue or 'No issues'",
          "remediation": "action or 'Promote'",
          "prLink": null,
          "repoUrl": null,
          "baseBranch": null
        }
        
        Confidence: 90-100 (clear), 70-89 (good), 50-69 (mixed), <50 (unclear)
        """;

    public AnalysisAgentService(ObjectMapper objectMapper, K8sTools k8sTools, ChatClient chatClient) {
        this.objectMapper = objectMapper;
        this.k8sTools = k8sTools;
        this.chatClient = chatClient;
    }

    public AnalysisResult analyze(String diagnosticData) {
        log.info("AnalysisAgent: Analyzing diagnostic data");

        try {
            // Call the model with function calling support
            OrchestrationModuleConfig conf = new OrchestrationModuleConfig().withLlmConfig(GPT_5);
            OrchestrationChatOptions options = new OrchestrationChatOptions(conf);

            UserMessage userMessage = new UserMessage(diagnosticData);
            Prompt userPrompt = new Prompt(userMessage);

            System.out.println("Calling new AI Agent");
            String response =
                    Objects.requireNonNull(chatClient
                            .prompt(userPrompt)
                            .system(SYSTEM_MESSAGE)
                            .options(options)
                            .tools(k8sTools)
                            .call()
                            .chatResponse()).getResult().getOutput().getText();

            log.info("DiagnosticAgent: Generated diagnostic report");
            log.info("Result: {}", response);
            response = response.replaceAll("```json", "").replaceAll("```", "").trim();
            AnalysisResult result = objectMapper.readValue(response, AnalysisResult.class);

            log.info("AnalysisAgent: Analysis complete - Promote: {}, Confidence: {}%",
                result.promote(), result.confidence());

            return result;

        } catch (Exception e) {
            log.error("Error in AnalysisAgent", e);
            // Return a default error result
            return new AnalysisResult(
                false,
                0,
                "Error during analysis: " + e.getMessage(),
                "Analysis failed",
                "Manual investigation required",
                null,
                null,
                null
            );
        }
    }
}