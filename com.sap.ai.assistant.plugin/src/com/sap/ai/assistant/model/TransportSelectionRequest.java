package com.sap.ai.assistant.model;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Encapsulates a transport selection request that blocks the agent thread
 * until the UI thread resolves it. Follows the same pattern as {@link DiffRequest}.
 */
public class TransportSelectionRequest {

    private final String objectName;
    private final String objectType;
    private final List<TransportEntry> availableTransports;

    private volatile TransportSelection selection;
    private final CountDownLatch latch = new CountDownLatch(1);

    public TransportSelectionRequest(String objectName, String objectType,
                                      List<TransportEntry> availableTransports) {
        this.objectName = objectName;
        this.objectType = objectType;
        this.availableTransports = availableTransports != null
                ? Collections.unmodifiableList(availableTransports)
                : Collections.emptyList();
    }

    /**
     * Called by the UI thread to resolve the transport selection.
     */
    public void setSelection(TransportSelection selection) {
        this.selection = selection;
        this.latch.countDown();
    }

    /**
     * Blocks the calling (agent) thread until a selection is made.
     */
    public void awaitSelection() throws InterruptedException {
        latch.await();
    }

    public TransportSelection getSelection() {
        return selection;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getObjectType() {
        return objectType;
    }

    public List<TransportEntry> getAvailableTransports() {
        return availableTransports;
    }

    /**
     * Represents an open transport request available for selection.
     */
    public static class TransportEntry {

        private final String number;
        private final String description;

        public TransportEntry(String number, String description) {
            this.number = number;
            this.description = description;
        }

        public String getNumber() {
            return number;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return number + " — " + (description != null ? description : "");
        }
    }
}
