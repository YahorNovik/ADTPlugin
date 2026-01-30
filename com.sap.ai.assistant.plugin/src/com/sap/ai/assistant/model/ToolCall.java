package com.sap.ai.assistant.model;

import com.google.gson.JsonObject;

/**
 * Represents a tool invocation requested by the LLM. Each tool call has a unique
 * identifier, the name of the tool to invoke, and a JSON object of arguments.
 */
public class ToolCall {

    private final String id;
    private final String name;
    private final JsonObject arguments;

    /**
     * Creates a new tool call.
     *
     * @param id        the unique identifier for this tool call
     * @param name      the name of the tool to invoke
     * @param arguments the arguments to pass to the tool
     */
    public ToolCall(String id, String name, JsonObject arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JsonObject getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "ToolCall{id='" + id + "', name='" + name + "', arguments=" + arguments + "}";
    }
}
