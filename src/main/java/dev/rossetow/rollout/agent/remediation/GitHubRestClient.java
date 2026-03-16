package dev.rossetow.rollout.agent.remediation;

/**
 * Interface for GitHub REST API operations
 */
public interface GitHubRestClient {
    
    GitHubRepository getRepository(String owner, String repo, String authorization);
    
    GitHubPullRequest createPullRequest(
        String owner,
        String repo,
        String authorization,
        CreatePullRequestRequest request
    );
    
    GitHubIssue createIssue(
        String owner,
        String repo,
        String authorization,
        CreateIssueRequest request
    );
}
