package dev.rossetow.rollout.agent.remediation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Map;

/**
 * Spring Component for GitHub Issue Tool - Migrated from Quarkus
 * Tool that creates GitHub issues for problems that need human attention.
 * Used when the agent identifies issues that cannot be automatically fixed.
 */
@Component
public class GitHubIssueToolSpring {
    
    private static final Logger log = LoggerFactory.getLogger(GitHubIssueToolSpring.class);
    
    private final String githubToken;
    private final GitHubRestClient githubClient;
    
    @Autowired
    public GitHubIssueToolSpring(GitHubRestClient githubClient) {
        this.githubToken = System.getenv("GITHUB_TOKEN");
        this.githubClient = githubClient;
        
        if (githubToken == null || githubToken.isEmpty()) {
            log.warn("GITHUB_TOKEN environment variable not set");
        } else {
            log.info("GitHub Issue tool initialized");
        }
    }
    
    /**
     * Create a GitHub issue to report a problem
     * This method is called by Spring AI function calling
     */
    public Map<String, Object> apply(
            String repoUrl,
            String title,
            String description,
            String rootCause,
            String namespace,
            String podName,
            String labels,
            String assignees
    ) {
        log.info("=== Executing Tool: createGitHubIssue ===");
        
        if (repoUrl == null || title == null || description == null) {
            return Map.of("success", false, "error", "Missing required parameters: repoUrl, title, description");
        }
        
        log.info(MessageFormat.format("Creating issue for repository: {0}", repoUrl));
        
        try {
            // Extract owner and repo from URL
            String[] ownerRepo = extractOwnerAndRepo(repoUrl);
            String owner = ownerRepo[0];
            String repo = ownerRepo[1];
            String authHeader = "Bearer " + (githubToken != null ? githubToken : "");
            
            // Build issue body
            String issueBody = generateIssueBody(description, rootCause, namespace, podName);
            
            // Parse labels and assignees with defensive sanitization
            // Strip out any brackets, quotes, or JSON-like formatting that LLM might add
            String sanitizedLabels = sanitizeInput(labels);
            String sanitizedAssignees = sanitizeInput(assignees);
            
            String[] labelArray = (sanitizedLabels != null && !sanitizedLabels.isEmpty())
                ? sanitizedLabels.split(",")
                : new String[0];
            String[] assigneeArray = (sanitizedAssignees != null && !sanitizedAssignees.isEmpty())
                ? sanitizedAssignees.split(",")
                : new String[0];
            
            // Trim whitespace and remove any remaining quotes from labels and assignees
            for (int i = 0; i < labelArray.length; i++) {
                labelArray[i] = labelArray[i].trim().replaceAll("^\"|\"$", "");
            }
            for (int i = 0; i < assigneeArray.length; i++) {
                assigneeArray[i] = assigneeArray[i].trim().replaceAll("^@", "");
            }
            
            // Create issue request
            CreateIssueRequest issueRequest =
                new CreateIssueRequest(title, issueBody, java.util.Arrays.asList(labelArray), java.util.Arrays.asList(assigneeArray));
            
            GitHubIssue issue =
                githubClient.createIssue(owner, repo, authHeader, issueRequest);
            
            log.info(MessageFormat.format("Successfully created issue: {0}", issue.htmlUrl()));
            
            return Map.of(
                "success", true,
                "issueUrl", issue.htmlUrl(),
                "issueNumber", issue.number()
            );
            
        } catch (Exception e) {
            log.error("Failed to create issue", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * Extract owner and repository name from URL
     * @return Array with [owner, repo]
     */
    private String[] extractOwnerAndRepo(String repoUrl) {
        // Handle formats: https://github.com/owner/repo or https://github.com/owner/repo.git
        String cleaned = repoUrl.replace("https://github.com/", "")
            .replace(".git", "");
        return cleaned.split("/", 2);
    }
    
    /**
     * Generate issue body with analysis results
     */
    private String generateIssueBody(
            String description,
            String rootCause,
            String namespace,
            String podName
    ) {
        if (rootCause == null || rootCause.isEmpty()) {
            rootCause = "Not available";
        }
        
        if (namespace == null) {
            namespace = "unknown";
        }
        
        if (podName == null) {
            podName = "unknown";
        }
        
        return String.format("""
            ## Problem Description
            %s
            
            ## Root Cause Analysis
            %s
            
            ## Related Kubernetes Resources
            - **Namespace**: `%s`
            - **Pod**: `%s`
            
            ---
            *This issue was automatically created by Kubernetes AI Agent*
            *Please review and take appropriate action*
            """,
            description,
            rootCause,
            namespace,
            podName
        );
    }
    
    /**
     * Sanitize input by removing JSON-like formatting that LLM might add
     * Strips brackets, quotes, and other special characters
     */
    private String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Remove square brackets, curly braces, and quotes
        // This handles cases like: ["label1","label2"], [label1,label2], "label1","label2"
        return input
            .replaceAll("[\\[\\]{}]", "")  // Remove brackets and braces
            .replaceAll("\"", "")           // Remove all quotes
            .replaceAll("'", "")            // Remove single quotes
            .trim();
    }
}