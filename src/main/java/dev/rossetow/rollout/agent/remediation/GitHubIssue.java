package dev.rossetow.rollout.agent.remediation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub Issue model
 */
public record GitHubIssue(
    Long id,
    Integer number,
    String state,
    String title,
    String body,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("updated_at") String updatedAt,
    @JsonProperty("closed_at") String closedAt
) {}