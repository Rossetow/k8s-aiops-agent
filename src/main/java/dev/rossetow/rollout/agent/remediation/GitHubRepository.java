package dev.rossetow.rollout.agent.remediation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub Repository model
 */
public record GitHubRepository(
    Long id,
    String name,
    @JsonProperty("full_name") String fullName,
    String description,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("default_branch") String defaultBranch,
    boolean fork,
    @JsonProperty("private") boolean isPrivate
) {}