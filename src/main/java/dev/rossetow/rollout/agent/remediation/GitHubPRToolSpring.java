package dev.rossetow.rollout.agent.remediation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Spring Component for GitHub PR Tool - Migrated from Quarkus
 * Tool that creates GitHub PRs with fixes.
 * Git operations are deterministic, only the fix content comes from AI.
 */
@Component
public class GitHubPRToolSpring {
    
    private static final Logger log = LoggerFactory.getLogger(GitHubPRToolSpring.class);
    
    private final GitOperations gitOps;
    private final String githubToken;
    private final GitHubRestClient githubClient;
    
    @Autowired
    public GitHubPRToolSpring(GitHubRestClient githubClient) {
        this.gitOps = new GitOperations();
        this.githubToken = System.getenv("GITHUB_TOKEN");
        this.githubClient = githubClient;
        
        if (githubToken == null || githubToken.isEmpty()) {
            log.warn("GITHUB_TOKEN environment variable not set");
        } else {
            log.info("GitHub PR tool initialized");
        }
    }
    
    // Package-private constructor for testing
    GitHubPRToolSpring(GitOperations gitOps, String githubToken, GitHubRestClient githubClient) {
        this.gitOps = gitOps;
        this.githubToken = githubToken;
        this.githubClient = githubClient;
    }
    
    /**
     * Create a GitHub pull request with code fixes
     * This method is called by Spring AI function calling
     */
    public Map<String, Object> apply(
            String repoUrl,
            Map<String, String> fileChanges,
            String fixDescription,
            String rootCause,
            String namespace,
            String podName,
            String testingRecommendations
    ) {
        log.info("=== Executing Tool: createGitHubPR ===");
        
        // Validate required parameters
        if (repoUrl == null || fileChanges == null || fixDescription == null) {
            return Map.of("success", false, "error", "Missing required parameters: repoUrl, fileChanges, fixDescription");
        }
        
        log.info(MessageFormat.format("Creating PR for repository: {0}", repoUrl));
        
        // Deterministic git workflow (HOW to fix):
        String branchName = "fix/k8s-issue-" + UUID.randomUUID().toString().substring(0, 8);
        Path repoPath = null;
        
        try {
            // 1. Clone (library)
            repoPath = gitOps.cloneRepository(repoUrl, githubToken);
            
            // 2. Create branch (library)
            gitOps.createBranch(repoPath, branchName);
            
            // 3. Apply AI-suggested changes (library file I/O)
            gitOps.applyChanges(repoPath, fileChanges);
            
            // 4. Commit and push (library)
            String commitMsg = formatCommitMessage(fixDescription);
            gitOps.commitAndPush(repoPath, commitMsg, githubToken);
            
            // 5. Create PR via GitHub REST API
            GitHubPullRequest pr = createPullRequestOnGitHub(
                repoUrl, branchName, fixDescription, rootCause,
                testingRecommendations, namespace, podName, fileChanges
            );
            
            log.info(MessageFormat.format("Successfully created PR: {0}", pr.htmlUrl()));
            
            return Map.of(
                "success", true,
                "prUrl", pr.htmlUrl(),
                "prNumber", pr.number(),
                "branch", branchName
            );
            
        } catch (Exception e) {
            log.error("Failed to create PR", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        } finally {
            // Cleanup temporary directory
            if (repoPath != null) {
                gitOps.cleanup(repoPath);
            }
        }
    }
    
    /**
     * Create a pull request on GitHub via REST API
     */
    private GitHubPullRequest createPullRequestOnGitHub(
            String repoUrl,
            String branchName,
            String fixDescription,
            String rootCause,
            String testingRecommendations,
            String namespace,
            String podName,
            Map<String, String> fileChanges
    ) throws Exception {
        String[] ownerRepo = extractOwnerAndRepo(repoUrl);
        String owner = ownerRepo[0];
        String repo = ownerRepo[1];
        String authHeader = formatAuthHeader(githubToken);
        
        // Get repository to find default branch
        GitHubRepository repository =
            githubClient.getRepository(owner, repo, authHeader);
        
        String baseBranch = repository.defaultBranch();
        String prTitle = MessageFormat.format("Fix: {0}", fixDescription);
        String prBody = generatePRBody(rootCause, fixDescription, testingRecommendations, namespace, podName, fileChanges);
        
        // Create pull request
        CreatePullRequestRequest prRequest =
            new CreatePullRequestRequest(prTitle, branchName, baseBranch, prBody);
        
        return githubClient.createPullRequest(owner, repo, authHeader, prRequest);
    }
    
    /**
     * Format commit message with conventional commit prefix
     */
    private String formatCommitMessage(String fixDescription) {
        return MessageFormat.format("fix: {0}", fixDescription);
    }
    
    /**
     * Format authorization header for GitHub API
     */
    private String formatAuthHeader(String token) {
        return "Bearer " + token;
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
     * Generate PR body with analysis results
     */
    private String generatePRBody(
            String rootCause,
            String fixDescription,
            String testingRecommendations,
            String namespace,
            String podName,
            Map<String, String> fileChanges
    ) {
        String changesSummary = fileChanges != null ? 
            String.join(", ", fileChanges.keySet()) : "No files changed";
        
        if (testingRecommendations == null || testingRecommendations.isEmpty()) {
            testingRecommendations = "Run existing test suite";
        }
        
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
            ## Root Cause Analysis
            %s
            
            ## Changes Made
            Modified files: %s
            
            %s
            
            ## Testing Recommendations
            %s
            
            ## Related Kubernetes Resources
            - **Namespace**: `%s`
            - **Pod**: `%s`
            
            ---
            *This PR was automatically generated by Kubernetes AI Agent*
            *Review carefully before merging*
            """,
            rootCause,
            changesSummary,
            fixDescription,
            testingRecommendations,
            namespace,
            podName
        );
    }
}