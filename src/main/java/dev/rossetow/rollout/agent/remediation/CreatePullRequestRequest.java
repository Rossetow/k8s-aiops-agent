package dev.rossetow.rollout.agent.remediation;

/**
 * Request model for creating a GitHub Pull Request
 */
public record CreatePullRequestRequest(
    String title,
    String body,
    String head,
    String base
) {}