package com.sap.ai.assistant.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.sap.ai.assistant.Activator;

/**
 * Initialises the default preference values for the AI Assistant plug-in.
 * <p>
 * This class is registered via the {@code org.eclipse.core.runtime.preferences}
 * extension point in {@code plugin.xml}.
 * </p>
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(PreferenceConstants.LLM_PROVIDER, "ANTHROPIC");
        store.setDefault(PreferenceConstants.LLM_API_KEY, "");
        store.setDefault(PreferenceConstants.LLM_MODEL, "");
        store.setDefault(PreferenceConstants.LLM_BASE_URL, "");
        store.setDefault(PreferenceConstants.LLM_MAX_TOKENS, 8192);
        store.setDefault(PreferenceConstants.LLM_MAX_INPUT_TOKENS, 100000);
        store.setDefault(PreferenceConstants.INCLUDE_CONTEXT, true);
        store.setDefault(PreferenceConstants.MCP_SERVERS,
                "[{\"name\":\"SAP Docs\",\"url\":\"https://mcp-sap-docs.marianzeis.de/mcp\",\"enabled\":true}]");
        store.setDefault(PreferenceConstants.SAP_SAVED_SYSTEMS, "[]");
    }
}
