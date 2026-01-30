package com.sap.ai.assistant.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.sap.AdtRestClient;

/**
 * Central registry that instantiates every {@link SapTool} implementation
 * and provides lookup by name.
 * <p>
 * Usage:
 * <pre>
 *   AdtRestClient client = ...;
 *   SapToolRegistry registry = new SapToolRegistry(client);
 *   SapTool tool = registry.get("sap_search_object");
 *   ToolResult result = tool.execute(args);
 * </pre>
 * </p>
 */
public class SapToolRegistry {

    private final Map<String, SapTool> toolsByName;

    /**
     * Create the registry, instantiating every tool with the given
     * ADT REST client.
     *
     * @param client the authenticated ADT REST client shared by all tools
     */
    public SapToolRegistry(AdtRestClient client) {
        Map<String, SapTool> map = new LinkedHashMap<>();

        register(map, new SearchObjectTool(client));
        register(map, new GetSourceTool(client));
        register(map, new SetSourceTool(client));
        register(map, new ObjectStructureTool(client));
        register(map, new NodeContentsTool(client));
        register(map, new LockTool(client));
        register(map, new UnlockTool(client));
        register(map, new ActivateTool(client));
        register(map, new SyntaxCheckTool(client));
        register(map, new AtcRunTool(client));
        register(map, new CreateObjectTool(client));
        register(map, new WriteAndCheckTool(client));
        register(map, new TransportInfoTool(client));
        register(map, new FindDefinitionTool(client));
        register(map, new UsageReferencesTool(client));

        this.toolsByName = Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, SapTool> map, SapTool tool) {
        map.put(tool.getName(), tool);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Look up a tool by its unique name.
     *
     * @param name the tool name (e.g. "sap_search_object")
     * @return the tool instance, or {@code null} if not found
     */
    public SapTool get(String name) {
        return toolsByName.get(name);
    }

    /**
     * Returns an unmodifiable list of all registered tools.
     *
     * @return all tool instances
     */
    public List<SapTool> getAll() {
        return new ArrayList<>(toolsByName.values());
    }

    /**
     * Returns the {@link ToolDefinition} for every registered tool,
     * ready to be serialized and sent to the LLM.
     *
     * @return list of all tool definitions
     */
    public List<ToolDefinition> getAllDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (SapTool tool : toolsByName.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }

    /**
     * Returns the number of registered tools.
     *
     * @return tool count
     */
    public int size() {
        return toolsByName.size();
    }

    @Override
    public String toString() {
        return "SapToolRegistry{tools=" + toolsByName.keySet() + "}";
    }
}
