package com.sap.ai.assistant.tools;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;

/**
 * Contract for every SAP ADT tool that can be invoked by the LLM.
 * <p>
 * Each tool exposes its name, a {@link ToolDefinition} (including the
 * JSON Schema for its parameters), and an {@code execute} method that
 * carries out the actual ADT REST call.
 * </p>
 */
public interface SapTool {

    /**
     * Returns the unique name of this tool (e.g. "sap_search_object").
     *
     * @return tool name
     */
    String getName();

    /**
     * Returns the full {@link ToolDefinition} that should be sent to
     * the LLM so it knows how to invoke this tool.
     *
     * @return the tool definition including parameter schema
     */
    ToolDefinition getDefinition();

    /**
     * Execute this tool with the given arguments.
     *
     * @param arguments the JSON object of arguments supplied by the LLM
     * @return the tool result (success content or error)
     * @throws Exception if an unrecoverable error occurs
     */
    ToolResult execute(JsonObject arguments) throws Exception;
}
