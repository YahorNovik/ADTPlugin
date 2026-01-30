package com.sap.ai.assistant.model;

import com.google.gson.JsonObject;

/**
 * Describes a tool that the LLM can invoke, including its name, a human-readable
 * description, and the JSON Schema that defines its parameters.
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonObject parametersSchema;

    /**
     * Creates a new tool definition.
     *
     * @param name             the unique tool name (used in tool calls)
     * @param description      a human-readable description of the tool's purpose
     * @param parametersSchema a JSON Schema object describing the expected parameters
     */
    public ToolDefinition(String name, String description, JsonObject parametersSchema) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getParametersSchema() {
        return parametersSchema;
    }

    @Override
    public String toString() {
        return "ToolDefinition{name='" + name + "', description='" + description + "'}";
    }
}
