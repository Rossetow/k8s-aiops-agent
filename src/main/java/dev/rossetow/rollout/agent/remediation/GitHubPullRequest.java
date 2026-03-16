package dev.rossetow.rollout.agent.remediation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub Pull Request model
 */
public record GitHubPullRequest(
    Long id,
    Integer number,
    String state,
    String title,
    String body,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("updated_at") String updatedAt,
    @JsonProperty("merged_at") String mergedAt,
    boolean merged,
    Head head,
    Base base
) {
    public record Head(
        String ref,
        String sha
    ) {}
    
    public record Base(
        String ref,
        String sha
    ) {}
}