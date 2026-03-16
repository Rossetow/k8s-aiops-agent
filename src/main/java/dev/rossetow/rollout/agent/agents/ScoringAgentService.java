package dev.rossetow.rollout.agent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.ai.sdk.orchestration.OrchestrationModuleConfig;
import com.sap.ai.sdk.orchestration.spring.OrchestrationChatOptions;
import dev.rossetow.rollout.agent.model.AnalysisResult;
import dev.rossetow.rollout.agent.model.ScoringResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.sap.ai.sdk.orchestration.OrchestrationAiModel.GPT_5;

/**
 * Scoring Agent Service - Migrated from LangChain4j to Spring AI
 * Evaluates analysis quality
 */
@Service
public class ScoringAgentService {

    private static final Logger log = LoggerFactory.getLogger(ScoringAgentService.class);

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    private static final String SYSTEM_MESSAGE = """
        /no_think
        
        BE CONCISE. Fast quality evaluation.
        
        JSON OUTPUT:
        {
          "score": 0-100,
          "needsRetry": true/false,
          "reason": "brief explanation"
        }
        
        Good: confidence >70%, clear root cause, actionable plan
        Retry: confidence <50%, unclear cause, no action
        """;

    public ScoringAgentService(ObjectMapper objectMapper, ChatClient chatClient) {
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
    }

    public ScoringResult evaluate(AnalysisResult analysisResult) {
        log.info("ScoringAgent: Evaluating analysis quality");

        try {
            // Serialize AnalysisResult to JSON for the prompt
            String analysisJson = objectMapper.writeValueAsString(analysisResult);

            // String response = chatModel.call(prompt).getResult().getOutput().getText();
            OrchestrationModuleConfig conf = new OrchestrationModuleConfig().withLlmConfig(GPT_5);
            OrchestrationChatOptions options = new OrchestrationChatOptions(conf);

            UserMessage userMessage = new UserMessage(analysisJson);
            Prompt userPrompt = new Prompt(userMessage);

            System.out.println("Calling new AI Agent");
            String response =
                    Objects.requireNonNull(chatClient
                            .prompt(userPrompt)
                            .system(SYSTEM_MESSAGE)
                            .options(options)
                            .call()
                            .chatResponse()).getResult().getOutput().getText();

            log.info("DiagnosticAgent: Generated diagnostic report");
            log.info("Result: {}", response);

            // Parse JSON response to ScoringResult
            response = response.replaceAll("```json", "").replaceAll("```", "").trim();

            ScoringResult result = objectMapper.readValue(response, ScoringResult.class);

            log.info("ScoringAgent: Score: {}, NeedsRetry: {}", result.score(), result.needsRetry());

            return result;

        } catch (Exception e) {
            log.error("Error in ScoringAgent", e);
            // Return a default result indicating retry is needed
            return new ScoringResult(0, true, "Scoring failed: " + e.getMessage());
        }
    }
}