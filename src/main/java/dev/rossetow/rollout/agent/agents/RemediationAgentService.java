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
 * Remediation Agent Service - Migrated from LangChain4j to Spring AI
 * Implements remediation fixes by creating GitHub PRs or Issues
 */
@Service
public class RemediationAgentService {

    private static final Logger log = LoggerFactory.getLogger(RemediationAgentService.class);

    private final K8sTools k8sTools;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    private static final String SYSTEM_MESSAGE = """
        /no_think
        
        You are a remediation agent. Your job is to take action based on analysis results.
        
        CRITICAL DECISION TREE (evaluate in order):
        1. If no repoUrl provided → return analysisResult unchanged
        2. If promote=false AND you can identify a SPECIFIC CODE FIX:
           a. Analyze diagnosticData and rootCause to determine if issue is code-fixable
           b. Code-fixable issues include: configuration errors, resource limits, environment variables,
              dependency versions, timeout values, retry logic, error handling
           c. If fixable → call createGitHubPR tool with specific file changes
           d. If NOT fixable (infrastructure, external dependencies, unclear) → call createGitHubIssue tool
        3. If promote=false AND cannot identify specific fix → call createGitHubIssue tool
        
        WHEN CREATING GITHUB PRs (PREFERRED for code-fixable issues):
        - Analyze the rootCause and diagnosticData to identify the exact files and changes needed
        - fileChanges: Map of file paths to complete new file content (e.g., {"src/main/resources/application.properties": "new content"})
        - fixDescription: Brief description of what the fix does
        - rootCause: Use rootCause field from analysisResult
        - namespace: Extract from diagnosticData
        - podName: Extract canary pod name from diagnosticData
        - testingRecommendations: Suggest how to verify the fix
        
        COMMON CODE FIXES TO LOOK FOR:
        - Memory/CPU limits too low → Update deployment YAML or application.properties
        - Missing environment variables → Add to deployment YAML
        - Wrong configuration values → Fix application.properties or config files
        - Dependency version conflicts → Update pom.xml or build files
        - Timeout values too aggressive → Adjust in config files
        - Missing error handling → Add try-catch or error responses
        
        WHEN CREATING GITHUB ISSUES (fallback for non-code issues):
        - Extract namespace and rolloutName from diagnosticData (look for "namespace:" and pod names)
        - Use podName from the canary pod in diagnosticData
        - title: "Canary Deployment Failed: [rootCause from analysisResult]"
        - description: Use the analysis field from analysisResult
        - rootCause: Use rootCause field from analysisResult
        - labels: "deployment-failure,canary" (comma-separated, NO brackets, NO quotes around the whole string)
        - assignees: "kdubois" (NO @ symbol, NO brackets, NO quotes around the whole string)
        
        AFTER CALLING THE TOOL:
        - If createGitHubPR succeeds, update analysisResult.prLink with the prUrl from the tool response
        - If createGitHubIssue succeeds, update analysisResult.prLink with the issueUrl from the tool response
        - Return the updated AnalysisResult as JSON
        
        OUTPUT FORMAT: Return ONLY the AnalysisResult JSON object. NO explanations, NO markdown.
        
        IMPORTANT: You MUST call a tool when conditions are met. Prioritize PRs over issues when a code fix is identifiable.
        """;

    public RemediationAgentService(ObjectMapper objectMapper, K8sTools k8sTools, ChatClient chatClient) {
        this.k8sTools = k8sTools;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
    }

    public AnalysisResult implementRemediation(
            String diagnosticData,
            AnalysisResult analysisResult,
            String repoUrl,
            String baseBranch
    ) {
        log.info("RemediationAgent: Implementing remediation");

        try {
            // Serialize AnalysisResult to JSON for the prompt
            String analysisJson = objectMapper.writeValueAsString(analysisResult);

            String userMessageBase = String.format("""
                Diagnostic data: %s

                Analysis result: %s
                Repository URL: %s
                Base branch: %s

                Implement remediation if needed and return the updated AnalysisResult with prLink set if a PR was created.
                Extract namespace, rolloutName, and pod names from the diagnostic data to use when creating GitHub issues.
                """, diagnosticData, analysisJson, repoUrl, baseBranch);

            // String response = chatModel.call(prompt).getResult().getOutput().getText();
            OrchestrationModuleConfig conf = new OrchestrationModuleConfig().withLlmConfig(GPT_5);
            OrchestrationChatOptions options = new OrchestrationChatOptions(conf);

            UserMessage userMessage = new UserMessage(userMessageBase);
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
            // Parse JSON response to AnalysisResult
            AnalysisResult result = objectMapper.readValue(response, AnalysisResult.class);

            log.info("RemediationAgent: Remediation complete, PR/Issue link: {}", result.prLink());

            return result;

        } catch (Exception e) {
            log.error("Error in RemediationAgent", e);
            // Return the original analysis result if remediation fails
            return analysisResult;
        }
    }
}