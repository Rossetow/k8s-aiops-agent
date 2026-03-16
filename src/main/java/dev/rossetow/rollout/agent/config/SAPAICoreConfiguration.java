package dev.rossetow.rollout.agent.config;

import com.sap.ai.sdk.core.AiCoreService;
import com.sap.ai.sdk.foundationmodels.openai.OpenAiClient;

import com.sap.ai.sdk.orchestration.OrchestrationClient;
import com.sap.ai.sdk.orchestration.OrchestrationModuleConfig;
import com.sap.ai.sdk.orchestration.spring.OrchestrationChatModel;
import com.sap.ai.sdk.orchestration.spring.OrchestrationChatOptions;
import dev.rossetow.rollout.agent.k8s.K8sTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import com.sap.ai.sdk.foundationmodels.openai.spring.OpenAiChatModel;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.observation.ObservationRegistry;

import java.time.Instant;

import static com.sap.ai.sdk.orchestration.OrchestrationAiModel.CLAUDE_4_SONNET;

/**
 * Configuration for SAP AI Core integration with Spring AI
 * Based on SAP AI SDK documentation: https://sap.github.io/ai-sdk/docs/java/spring-ai/openai
 */
@Configuration
public class SAPAICoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SAPAICoreConfiguration.class);

    private final SAPAICoreProperties properties;
    private final K8sTools k8sTools;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o}")
    private String modelName;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    // Token caching
    private String cachedToken;
    private Instant tokenExpiry;

    public SAPAICoreConfiguration(SAPAICoreProperties properties, K8sTools k8sTools) {
        this.properties = properties;
        this.k8sTools = k8sTools;
    }

    /**
     * Creates WebClient.Builder for reactive HTTP operations
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Creates OpenAiApi configured for SAP AI Core
     * This follows the SAP AI SDK pattern for Spring AI integration
     */
    @Bean
    public OpenAiApi openAiApi(WebClient.Builder webClientBuilder) {
        log.info("Configuring OpenAiApi for SAP AI Core");
        log.info("API URL: {}", properties.getApiUrl());
        log.info("Model: {}", modelName);
        log.info("Deployment ID: {}", properties.getDeploymentId());
        log.info("Resource Group: {}", properties.getResourceGroup());

        // SAP AI Core OpenAI-compatible endpoint structure:
        // {api-url}/v2/inference/deployments/{deployment-id}
        // Spring AI will append /chat/completions
        String baseUrl = String.format("%s/v2/inference/deployments/%s",
            properties.getApiUrl(),
            properties.getDeploymentId()
        );

        log.info("Constructed base URL: {}", baseUrl);

        // Get OAuth token for SAP AI Core with caching
        String token = getOrRefreshToken();

        // Create RestClient.Builder with SAP AI Core configuration
        // Using request interceptor to add dynamic headers including token refresh
        RestClient.Builder restClientBuilder = RestClient.builder()
            .baseUrl(baseUrl)
            .requestInterceptor((request, body, execution) -> {
                request.getHeaders().setBearerAuth(getOrRefreshToken());
                request.getHeaders().set("AI-Resource-Group", properties.getResourceGroup());
                request.getHeaders().set("Content-Type", "application/json");
                return execution.execute(request, body);
            });

        // Configure WebClient.Builder with SAP AI Core settings
        WebClient.Builder configuredWebClientBuilder = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("AI-Resource-Group", properties.getResourceGroup())
            .defaultHeader("Content-Type", "application/json")
            .filter((request, next) -> {
                // Add token dynamically to each request
                request.headers().setBearerAuth(getOrRefreshToken());
                return next.exchange(request);
            });

        // Create OpenAiApi with the configured RestClient and WebClient builders
        MultiValueMap<String, String> defaultHeaders = new LinkedMultiValueMap<>();
        defaultHeaders.add("AI-Resource-Group", properties.getResourceGroup());

        return OpenAiApi.builder().apiKey(token).baseUrl(baseUrl).restClientBuilder(restClientBuilder).webClientBuilder(configuredWebClientBuilder).completionsPath("/completion").build();
    }

    /**
     * Creates ToolCallingManager bean for managing tool calling operations
     */
    @Bean
    public ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    /**
     * Creates RetryTemplate bean for retry logic
     */
    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
    }

    /**
     * Creates ObservationRegistry bean for observability
     */
    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.NOOP;
    }

    /**
     * Creates ChatModel bean configured for SAP AI Core
     */
    @Bean
    public ChatModel chatModel(OpenAiApi openAiApi, ToolCallingManager toolCallingManager, RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
        log.info("Creating ChatModel with SAP AI Core configuration");

        // Create Spring AI OpenAiChatModel with SAP AI Core API
        // Required parameters: openAiApi, options, toolCallingManager, retryTemplate, observationRegistry

        return new org.springframework.ai.openai.OpenAiChatModel(
                openAiApi,
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build(),
                toolCallingManager,
                retryTemplate,
                observationRegistry
        );
    }

    /**
     * Gets or refreshes OAuth token for SAP AI Core with caching
     */
    private synchronized String getOrRefreshToken() {
        // Check if we have a valid cached token
        if (cachedToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            log.debug("Using cached OAuth token");
            return cachedToken;
        }

        // Token is expired or doesn't exist, fetch new one
        log.info("Obtaining new OAuth token for SAP AI Core");
        return refreshToken();
    }

    /**
     * Gets OAuth token for SAP AI Core using client credentials
     * This implements the OAuth2 client credentials flow for SAP AI Core
     */
    private String refreshToken() {
        try {
            // Create OAuth token request
            RestClient tokenClient = RestClient.builder()
                .baseUrl(properties.getAuthUrl())
                .build();

            // Request token using client credentials
            String tokenResponse = tokenClient.post()
                .uri("/oauth/token?grant_type=client_credentials")
                .header("Authorization", createBasicAuthHeader(properties.getClientId(), properties.getClientSecret()))
                .retrieve()
                .body(String.class);

            if (tokenResponse == null || tokenResponse.isEmpty()) {
                throw new RuntimeException("Empty token response from SAP AI Core");
            }

            // Parse token and expiry from response
            // Expected format: {"access_token":"...", "token_type":"bearer", "expires_in":3600}
            cachedToken = extractAccessToken(tokenResponse);
            int expiresIn = extractExpiresIn(tokenResponse);
            
            // Set expiry with 5 minute buffer to avoid edge cases
            tokenExpiry = Instant.now().plusSeconds(expiresIn - 300);
            
            log.info("Successfully obtained OAuth token (expires in {} seconds)", expiresIn);
            return cachedToken;

        } catch (Exception e) {
            log.error("Failed to obtain OAuth token from SAP AI Core: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to authenticate with SAP AI Core: " + e.getMessage(), e);
        }
    }

    /**
     * Creates Basic Auth header for OAuth token request
     */
    private String createBasicAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = java.util.Base64.getEncoder()
            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Basic " + encodedCredentials;
    }

    /**
     * Extracts access token from OAuth response JSON
     */
    private String extractAccessToken(String jsonResponse) {
        try {
            // Simple JSON parsing for access_token field
            // Format: {"access_token":"TOKEN_VALUE","token_type":"bearer",...}
            int startIndex = jsonResponse.indexOf("\"access_token\":\"");
            if (startIndex == -1) {
                throw new RuntimeException("access_token not found in response");
            }
            startIndex += 16;
            int endIndex = jsonResponse.indexOf("\"", startIndex);
            if (endIndex == -1) {
                throw new RuntimeException("Malformed access_token in response");
            }
            return jsonResponse.substring(startIndex, endIndex);
        } catch (Exception e) {
            log.error("Failed to parse access token from response: {}", jsonResponse, e);
            throw new RuntimeException("Failed to parse OAuth token response", e);
        }
    }

    /**
     * Extracts expires_in from OAuth response JSON
     */
    private int extractExpiresIn(String jsonResponse) {
        try {
            // Simple JSON parsing for expires_in field
            // Format: {"access_token":"...","expires_in":3600,...}
            int startIndex = jsonResponse.indexOf("\"expires_in\":");
            if (startIndex == -1) {
                log.warn("expires_in not found in response, defaulting to 3600 seconds");
                return 3600; // Default to 1 hour
            }
            startIndex += 13;
            
            // Find the next comma or closing brace
            int endIndex = jsonResponse.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = jsonResponse.indexOf("}", startIndex);
            }
            
            String expiresInStr = jsonResponse.substring(startIndex, endIndex).trim();
            return Integer.parseInt(expiresInStr);
        } catch (Exception e) {
            log.warn("Failed to parse expires_in from response, defaulting to 3600 seconds: {}", e.getMessage());
            return 3600; // Default to 1 hour
        }
    }

    /**
     * Alternative bean using SAP AI SDK's OpenAiChatModel
     * This can be used instead of Spring AI's OpenAiChatModel if needed
     */
    @Bean(name = "sapAiSdkChatModel")
    public OpenAiChatModel sapAiSdkOpenAiChatModel(OpenAiApi openAiApi) {
        log.info("Creating SAP AI SDK OpenAiChatModel as alternative");

        var destination = new AiCoreService()
                .getInferenceDestination(properties.getResourceGroup())
                .usingDeploymentId(properties.getDeploymentId());

        // This uses Spring AI's OpenAiChatModel with SAP AI Core API
        return new OpenAiChatModel(
                OpenAiClient.withCustomDestination(destination)
        );
    }

    /**
     * Alternative bean using SAP AI SDK's OpenAiChatModel
     * This can be used instead of Spring AI's OpenAiChatModel if needed
     */
    @Bean(name = "chatClient")
    public ChatClient chatClient() {
        log.info("Creating SAP AI SDK OpenAiChatModel as alternative");
        OrchestrationModuleConfig conf = new OrchestrationModuleConfig().withLlmConfig(CLAUDE_4_SONNET);
        OrchestrationChatOptions options = new OrchestrationChatOptions(conf);
        ChatClient chatClient = ChatClient.create(new OrchestrationChatModel());

        return chatClient;
    }
}