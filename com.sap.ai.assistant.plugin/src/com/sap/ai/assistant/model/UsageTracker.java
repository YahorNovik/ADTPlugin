package com.sap.ai.assistant.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates {@link RequestLogEntry} objects for a session and provides
 * running totals for token usage.
 */
public class UsageTracker {

    private final List<RequestLogEntry> entries = new ArrayList<>();
    private int totalInputTokens;
    private int totalOutputTokens;

    public synchronized void addEntry(RequestLogEntry entry) {
        entries.add(entry);
        if (entry.getUsage() != null) {
            totalInputTokens += entry.getUsage().getInputTokens();
            totalOutputTokens += entry.getUsage().getOutputTokens();
        }
    }

    public synchronized List<RequestLogEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized int getTotalInputTokens() { return totalInputTokens; }
    public synchronized int getTotalOutputTokens() { return totalOutputTokens; }
    public synchronized int getTotalTokens() { return totalInputTokens + totalOutputTokens; }
    public synchronized int getRequestCount() { return entries.size(); }

    public synchronized void clear() {
        entries.clear();
        totalInputTokens = 0;
        totalOutputTokens = 0;
    }

    /**
     * Returns a text summary of all entries, suitable for copying to clipboard.
     */
    public synchronized String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("SAP AI Assistant - Request Log\n");
        sb.append("========================================\n\n");
        for (RequestLogEntry entry : entries) {
            sb.append(entry.toDetailString()).append("\n");
        }
        sb.append("========================================\n");
        sb.append("Total: ").append(entries.size()).append(" requests, ");
        sb.append(totalInputTokens).append(" input tokens, ");
        sb.append(totalOutputTokens).append(" output tokens\n");
        return sb.toString();
    }
}
