package com.sap.ai.assistant.model;

/**
 * Holds the user's transport choice for a session.
 * <p>
 * The selection is made once (on the first write operation) and reused
 * for all subsequent create/modify operations in the same chat session.
 * </p>
 */
public class TransportSelection {

    public enum Mode { LOCAL, EXISTING_TRANSPORT, NEW_TRANSPORT }

    private final Mode mode;
    private final String transportNumber;
    private final String newTransportDescription;

    private TransportSelection(Mode mode, String transportNumber, String newTransportDescription) {
        this.mode = mode;
        this.transportNumber = transportNumber;
        this.newTransportDescription = newTransportDescription;
    }

    /** Save all objects as local ($TMP). */
    public static TransportSelection local() {
        return new TransportSelection(Mode.LOCAL, null, null);
    }

    /** Use an existing transport request. */
    public static TransportSelection withTransport(String transportNumber) {
        return new TransportSelection(Mode.EXISTING_TRANSPORT, transportNumber, null);
    }

    /** Create a new transport request with the given description. */
    public static TransportSelection newTransport(String description) {
        return new TransportSelection(Mode.NEW_TRANSPORT, null, description);
    }

    public Mode getMode() {
        return mode;
    }

    public String getTransportNumber() {
        return transportNumber;
    }

    public String getNewTransportDescription() {
        return newTransportDescription;
    }

    public boolean isLocal() {
        return mode == Mode.LOCAL;
    }
}
