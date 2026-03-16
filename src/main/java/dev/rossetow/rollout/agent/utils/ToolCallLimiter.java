package dev.rossetow.rollout.agent.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits tool calls per session to prevent rate limiting issues.
 * Tracks tool calls by memory ID and enforces a maximum limit.
 */
public class ToolCallLimiter {
    
    private static final Logger log = LoggerFactory.getLogger(ToolCallLimiter.class);
    
    private static final int MAX_TOOL_CALLS = 4;
    private static final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Integer>> toolCallHistory = new ConcurrentHashMap<>();
    
    /**
     * Check if a tool call should be allowed
     * @param memoryId The session/memory ID
     * @param toolName The name of the tool being called
     * @param params String representation of parameters for duplicate detection
     * @return true if the call should be allowed, false otherwise
     */
    public static boolean allowToolCall(String memoryId, String toolName, String params) {
        // Get or create call count for this session
        AtomicInteger count = callCounts.computeIfAbsent(memoryId, k -> new AtomicInteger(0));
        
        // Check if we've exceeded the limit
        if (count.get() >= MAX_TOOL_CALLS) {
            log.warn("Tool call limit ({}) reached for session {}. Rejecting call to {}",
                MAX_TOOL_CALLS, memoryId, toolName);
            return false;
        }
        
        // Check for duplicate calls
        Map<String, Integer> history = toolCallHistory.computeIfAbsent(memoryId, k -> new ConcurrentHashMap<>());
        String callKey = toolName + ":" + params;
        
        if (history.containsKey(callKey)) {
            log.warn("Duplicate tool call detected for session {}: {} with params {}",
                memoryId, toolName, params);
            return false;
        }
        
        // Record this call
        history.put(callKey, count.incrementAndGet());
        log.info("Tool call {}/{} for session {}: {}",
            count.get(), MAX_TOOL_CALLS, memoryId, toolName);
        
        return true;
    }
    
    /**
     * Reset the call count for a session (call this when starting a new analysis)
     */
    public static void resetSession(String memoryId) {
        callCounts.remove(memoryId);
        toolCallHistory.remove(memoryId);
        log.debug("Reset tool call limiter for session {}", memoryId);
    }
    
    /**
     * Get the current call count for a session
     */
    public static int getCallCount(String memoryId) {
        AtomicInteger count = callCounts.get(memoryId);
        return count != null ? count.get() : 0;
    }
    
    /**
     * Clean up old sessions (call periodically to prevent memory leaks)
     */
    public static void cleanup() {
        // In a production system, you'd want to track timestamps and remove old entries
        // For now, we'll keep it simple
        if (callCounts.size() > 100) {
            log.warn("Tool call limiter has many sessions, consider implementing cleanup");
        }
    }
}

