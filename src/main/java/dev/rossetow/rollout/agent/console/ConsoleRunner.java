package dev.rossetow.rollout.agent.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.rossetow.rollout.agent.workflow.KubernetesWorkflowService;
import dev.rossetow.rollout.agent.model.AnalysisResult;

/**
 * Handles console mode operation for the Kubernetes Agent
 */
@Component
public class ConsoleRunner {
    
    private static final Logger log = LoggerFactory.getLogger(ConsoleRunner.class);
    
    private final KubernetesWorkflowService kubernetesWorkflowService;
    private final ExecutorService executorService;
    private boolean consoleMode = false;
    
    public ConsoleRunner(KubernetesWorkflowService kubernetesWorkflowService) {
        this.kubernetesWorkflowService = kubernetesWorkflowService;
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Start console mode if requested via command line arguments
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        // Check if console mode is enabled via system property
        String runMode = System.getProperty("run.mode");
        if ("console".equals(runMode)) {
            consoleMode = true;
            log.info("Starting Kubernetes AI Agent in console mode");
            executorService.submit(this::runConsoleMode);
        }
    }
    
    /**
     * Cleanup on shutdown
     */
    @PreDestroy
    public void onStop() {
        if (consoleMode) {
            log.info("Shutting down console mode");
            executorService.shutdown();
        }
    }
    
    /**
     * Run the agent in console mode.
     * Uses "console-session" as the memory ID to maintain conversation history
     * throughout the console session.
     */
    private void runConsoleMode() {
        log.info("Kubernetes AI Agent started in console mode. Type 'quit' to exit.");
        
        // Use a fixed memory ID for the console session to maintain conversation history
        final String memoryId = "console-session";
        log.info("Console session memory ID: {}", memoryId);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Exiting Kubernetes Agent. Goodbye!");
        }));
        
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();
                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }
                
                System.out.print("\nAgent > ");
                // Execute the workflow for console mode
                AnalysisResult result = kubernetesWorkflowService.execute(memoryId, userInput, "main");
                
                // Format the response for console output
                System.out.println("\n=== Analysis ===");
                System.out.println(result.analysis());
                System.out.println("\n=== Root Cause ===");
                System.out.println(result.rootCause());
                System.out.println("\n=== Remediation ===");
                System.out.println(result.remediation());
                System.out.println("\n=== Decision ===");
                System.out.println("Promote: " + result.promote());
                System.out.println("Confidence: " + result.confidence() + "%");
                if (result.prLink() != null) {
                    System.out.println("PR Link: " + result.prLink());
                }
            }
        } catch (Exception e) {
            log.error("Error in console mode", e);
        }
    }
}

