package dev.rossetow.rollout.agent.agents;

import dev.rossetow.rollout.agent.k8s.K8sTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import com.sap.ai.sdk.orchestration.OrchestrationModuleConfig;
import com.sap.ai.sdk.orchestration.spring.OrchestrationChatOptions;

import java.util.Objects;

import static com.sap.ai.sdk.orchestration.OrchestrationAiModel.GPT_5;

/**
 * Diagnostic Agent Service - Migrated from LangChain4j to Spring AI
 * Gathers Kubernetes diagnostic data using the getCanaryDiagnostics function
 */
@Service
public class DiagnosticAgentService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticAgentService.class);

    private final K8sTools k8sTools;
    private final ChatClient chatClient;

    private static final String SYSTEM_MESSAGE = """
        /no_think
        
        BE CONCISE. NO verbose reasoning. Time-critical K8s diagnostics.
        
        CONTEXT EXTRACTION:
        Extract from the user message:
        
        USE THE DEFAULT
        - namespace: rw-dcom
        - rolloutName: istio-rollout - the name of the Argo Rollout
        - containerName: istio-rollout
        
        WORKFLOW - Use getCanaryDiagnostics tool (ONE call):
        getCanaryDiagnostics("rw-dcom", "istio-rollout", "istio-rollout", 200)
        
        This fetches both stable and canary pod info and logs for the specific rollout.
        
        REPORT (max 800 chars):
        === DIAGNOSTIC REPORT ===
        STABLE: <pod status from result>
        CANARY: <pod status from result>
        STABLE LOGS: <key errors only>
        CANARY LOGS: <key errors only>
        SUMMARY: <1 sentence>
        === END ===
        """;

    public DiagnosticAgentService(K8sTools k8sTools, ChatClient chatClient) {
        this.k8sTools = k8sTools;
        this.chatClient = chatClient;
    }

    public String gatherDiagnostics(String message) {
        log.info("DiagnosticAgent: Gathering diagnostics for message: {}", message);

        try {
            // Call the model with function calling support
            OrchestrationModuleConfig conf = new OrchestrationModuleConfig().withLlmConfig(GPT_5);
            OrchestrationChatOptions options = new OrchestrationChatOptions(conf);

            UserMessage userMessage = new UserMessage("Gather diagnostic data for: " + message);
            Prompt userPrompt = new Prompt(userMessage);

            System.out.println("Calling new AI Agent");
            String result =
                    Objects.requireNonNull(chatClient
                            .prompt(userPrompt)
                            .system(SYSTEM_MESSAGE)
                            .options(options)
                            .tools(k8sTools)
                            .call()
                            .chatResponse()).getResult().getOutput().getText();

            log.info("DiagnosticAgent: Generated diagnostic report");
            log.info("Result: {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error in DiagnosticAgent", e);
            return "Error gathering diagnostics: " + e.getMessage();
        }
    }
}