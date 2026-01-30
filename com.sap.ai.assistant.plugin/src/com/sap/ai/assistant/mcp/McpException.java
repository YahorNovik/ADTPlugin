package com.sap.ai.assistant.mcp;

/**
 * Exception thrown when an MCP operation fails.
 */
public class McpException extends Exception {

    private static final long serialVersionUID = 1L;

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}
