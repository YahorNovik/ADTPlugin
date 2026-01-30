package com.sap.ai.assistant.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.sap.ai.assistant.Activator;

/**
 * Preference page for the SAP AI Assistant plug-in.
 * <p>
 * Allows users to select the LLM provider, enter the API key,
 * choose a model, configure max tokens, and toggle editor context
 * inclusion.
 * </p>
 */
public class AiAssistantPreferencePage extends FieldEditorPreferencePage
        implements IWorkbenchPreferencePage {

    /** Provider choices shown in the combo. */
    private static final String[][] PROVIDER_ENTRIES = {
        { "Anthropic", "ANTHROPIC" },
        { "OpenAI",    "OPENAI"    },
        { "Google",    "GOOGLE"    },
        { "Mistral",   "MISTRAL"   }
    };

    public AiAssistantPreferencePage() {
        super(GRID);
        setDescription("Configure the SAP AI Assistant LLM connection and behaviour.");
    }

    // ------------------------------------------------------------------
    // IWorkbenchPreferencePage
    // ------------------------------------------------------------------

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
    }

    // ------------------------------------------------------------------
    // FieldEditorPreferencePage
    // ------------------------------------------------------------------

    @Override
    protected void createFieldEditors() {
        // LLM provider selection
        addField(new ComboFieldEditor(
                PreferenceConstants.LLM_PROVIDER,
                "LLM Provider:",
                PROVIDER_ENTRIES,
                getFieldEditorParent()));

        // API key (masked input)
        StringFieldEditor apiKeyEditor = new StringFieldEditor(
                PreferenceConstants.LLM_API_KEY,
                "API Key:",
                getFieldEditorParent());
        apiKeyEditor.getTextControl(getFieldEditorParent())
                .setEchoChar('*');
        addField(apiKeyEditor);

        // Model identifier
        addField(new StringFieldEditor(
                PreferenceConstants.LLM_MODEL,
                "Model:",
                getFieldEditorParent()));

        // Max tokens
        IntegerFieldEditor maxTokensEditor = new IntegerFieldEditor(
                PreferenceConstants.LLM_MAX_TOKENS,
                "Max Tokens:",
                getFieldEditorParent());
        maxTokensEditor.setValidRange(256, 128000);
        addField(maxTokensEditor);

        // Include editor context
        addField(new BooleanFieldEditor(
                PreferenceConstants.INCLUDE_CONTEXT,
                "Include editor context in prompts",
                getFieldEditorParent()));
    }
}
