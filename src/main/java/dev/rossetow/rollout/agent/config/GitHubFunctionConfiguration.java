package dev.rossetow.rollout.agent.config;

import dev.rossetow.rollout.agent.remediation.GitHubIssueToolSpring;
import dev.rossetow.rollout.agent.remediation.GitHubPRToolSpring;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

/**
 * Configuration for GitHub function callbacks used by Spring AI
 * These beans are automatically registered as available functions for the AI model
 */
@Configuration
public class GitHubFunctionConfiguration {

    /**
     * GitHub PR creation function bean
     * The bean name "createGitHubPR" is used as the function name by Spring AI
     */
    @Bean
    public Function<GitHubPRRequest, Map<String, Object>> createGitHubPR(GitHubPRToolSpring gitHubPRTool) {
        return request -> gitHubPRTool.apply(
            request.repoUrl(),
            request.fileChanges(),
            request.fixDescription(),
            request.rootCause(),
            request.namespace(),
            request.podName(),
            request.testingRecommendations()
        );
    }

    /**
     * GitHub Issue creation function bean
     * The bean name "createGitHubIssue" is used as the function name by Spring AI
     */
    @Bean
    public Function<GitHubIssueRequest, Map<String, Object>> createGitHubIssue(GitHubIssueToolSpring gitHubIssueTool) {
        return request -> gitHubIssueTool.apply(
            request.repoUrl(),
            request.title(),
            request.description(),
            request.rootCause(),
            request.namespace(),
            request.podName(),
            request.labels(),
            request.assignees()
        );
    }

    /**
     * Request record for GitHub PR function
     */
    public record GitHubPRRequest(
        String repoUrl,
        Map<String, String> fileChanges,
        String fixDescription,
        String rootCause,
        String namespace,
        String podName,
        String testingRecommendations
    ) {}

    /**
     * Request record for GitHub Issue function
     */
    public record GitHubIssueRequest(
        String repoUrl,
        String title,
        String description,
        String rootCause,
        String namespace,
        String podName,
        String labels,
        String assignees
    ) {}
}
