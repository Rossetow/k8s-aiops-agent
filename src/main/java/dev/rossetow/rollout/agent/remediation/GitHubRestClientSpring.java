package dev.rossetow.rollout.agent.remediation;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring WebClient for GitHub API - Migrated from Quarkus REST Client
 */
@Component
public class GitHubRestClientSpring implements GitHubRestClient {

    private final WebClient webClient;

    public GitHubRestClientSpring(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.github.com/repos")
            .build();
    }

    @Override
    public GitHubRepository getRepository(String owner, String repo, String authorization) {
        return webClient.get()
            .uri("/{owner}/{repo}", owner, repo)
            .header("Authorization", authorization)
            .retrieve()
            .bodyToMono(GitHubRepository.class)
            .block();
    }

    @Override
    public GitHubPullRequest createPullRequest(
            String owner,
            String repo,
            String authorization,
            CreatePullRequestRequest request
    ) {
        return webClient.post()
            .uri("/{owner}/{repo}/pulls", owner, repo)
            .header("Authorization", authorization)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(GitHubPullRequest.class)
            .block();
    }

    @Override
    public GitHubIssue createIssue(
            String owner,
            String repo,
            String authorization,
            CreateIssueRequest request
    ) {
        return webClient.post()
            .uri("/{owner}/{repo}/issues", owner, repo)
            .header("Authorization", authorization)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(GitHubIssue.class)
            .block();
    }
}