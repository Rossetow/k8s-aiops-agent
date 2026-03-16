package dev.rossetow.rollout.agent.remediation;

import java.util.List;

/**
 * Request model for creating a GitHub Issue
 */
public record CreateIssueRequest(
    String title,
    String body,
    List<String> labels,
    List<String> assignees
) {}