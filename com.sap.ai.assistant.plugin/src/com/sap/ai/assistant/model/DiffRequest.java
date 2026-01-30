package com.sap.ai.assistant.model;

import java.util.concurrent.CountDownLatch;

/**
 * Encapsulates a proposed source code change that requires user approval
 * before being written to SAP. The background agent thread blocks on
 * {@link #awaitDecision()} until the UI thread calls {@link #setDecision}.
 */
public class DiffRequest {

    public enum Decision { PENDING, ACCEPTED, REJECTED, EDITED }

    private final String toolCallId;
    private final String toolName;
    private final String objectName;
    private final String sourceUrl;
    private final String oldSource;
    private final String newSource;

    private String editedSource;
    private volatile Decision decision = Decision.PENDING;
    private final CountDownLatch latch = new CountDownLatch(1);

    public DiffRequest(String toolCallId, String toolName, String objectName,
                       String sourceUrl, String oldSource, String newSource) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.objectName = objectName;
        this.sourceUrl = sourceUrl;
        this.oldSource = oldSource != null ? oldSource : "";
        this.newSource = newSource != null ? newSource : "";
    }

    /**
     * Called by the UI to resolve the approval.
     */
    public void setDecision(Decision decision) {
        this.decision = decision;
        this.latch.countDown();
    }

    /**
     * Called by the UI to resolve with edited source.
     */
    public void setDecision(Decision decision, String editedSource) {
        this.editedSource = editedSource;
        this.decision = decision;
        this.latch.countDown();
    }

    /**
     * Blocks the calling thread until a decision is made.
     * Throws {@link InterruptedException} if the thread is interrupted
     * (e.g. by Eclipse Job cancellation).
     */
    public void awaitDecision() throws InterruptedException {
        latch.await();
    }

    /**
     * Returns the final source: edited text if {@code EDITED}, otherwise
     * the original proposed new source.
     */
    public String getFinalSource() {
        if (decision == Decision.EDITED && editedSource != null) {
            return editedSource;
        }
        return newSource;
    }

    // -- Getters --

    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }
    public String getObjectName() { return objectName; }
    public String getSourceUrl() { return sourceUrl; }
    public String getOldSource() { return oldSource; }
    public String getNewSource() { return newSource; }
    public String getEditedSource() { return editedSource; }
    public Decision getDecision() { return decision; }
}
