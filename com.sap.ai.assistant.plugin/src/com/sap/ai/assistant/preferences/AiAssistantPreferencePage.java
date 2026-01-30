package com.sap.ai.assistant.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Button;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.sap.ai.assistant.Activator;
import com.sap.ai.assistant.model.LlmProviderConfig;

/**
 * Preference page for the SAP AI Assistant plug-in.
 * <p>
 * Uses a custom layout with a dynamic model dropdown that updates
 * when the provider selection changes.
 * </p>
 */
public class AiAssistantPreferencePage extends PreferencePage
        implements IWorkbenchPreferencePage {

    private Combo providerCombo;
    private Text apiKeyText;
    private Combo modelCombo;
    private Spinner maxTokensSpinner;
    private Button includeContextCheck;

    private final LlmProviderConfig.Provider[] providers = LlmProviderConfig.Provider.values();

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Configure the SAP AI Assistant LLM connection and behaviour.");
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        container.setLayout(layout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // -- LLM Connection group --
        Group llmGroup = new Group(container, SWT.NONE);
        llmGroup.setText("LLM Connection");
        llmGroup.setLayout(new GridLayout(2, false));
        llmGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Provider
        new Label(llmGroup, SWT.NONE).setText("Provider:");
        providerCombo = new Combo(llmGroup, SWT.READ_ONLY | SWT.DROP_DOWN);
        providerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (LlmProviderConfig.Provider p : providers) {
            providerCombo.add(p.getDisplayName());
        }
        providerCombo.addListener(SWT.Selection, e -> onProviderChanged());

        // API Key
        new Label(llmGroup, SWT.NONE).setText("API Key:");
        apiKeyText = new Text(llmGroup, SWT.BORDER | SWT.PASSWORD);
        apiKeyText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Model
        new Label(llmGroup, SWT.NONE).setText("Model:");
        modelCombo = new Combo(llmGroup, SWT.DROP_DOWN);
        modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Max Tokens
        new Label(llmGroup, SWT.NONE).setText("Max Tokens:");
        maxTokensSpinner = new Spinner(llmGroup, SWT.BORDER);
        maxTokensSpinner.setMinimum(256);
        maxTokensSpinner.setMaximum(128000);
        maxTokensSpinner.setIncrement(1024);
        maxTokensSpinner.setPageIncrement(4096);
        maxTokensSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // -- Behaviour group --
        Group behaviourGroup = new Group(container, SWT.NONE);
        behaviourGroup.setText("Behaviour");
        behaviourGroup.setLayout(new GridLayout(1, false));
        behaviourGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        includeContextCheck = new Button(behaviourGroup, SWT.CHECK);
        includeContextCheck.setText("Include editor context in prompts");
        includeContextCheck.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Load current values
        loadValues();

        return container;
    }

    private void onProviderChanged() {
        int idx = providerCombo.getSelectionIndex();
        if (idx < 0 || idx >= providers.length) return;

        LlmProviderConfig.Provider provider = providers[idx];
        String currentModel = modelCombo.getText();

        // Update model dropdown with this provider's models
        modelCombo.removeAll();
        for (String model : provider.getAvailableModels()) {
            modelCombo.add(model);
        }

        // Try to keep the current model if it exists in the new provider
        int found = -1;
        for (int i = 0; i < provider.getAvailableModels().length; i++) {
            if (provider.getAvailableModels()[i].equals(currentModel)) {
                found = i;
                break;
            }
        }

        if (found >= 0) {
            modelCombo.select(found);
        } else {
            // Select the default model for this provider
            modelCombo.select(0);
        }
    }

    private void loadValues() {
        IPreferenceStore store = getPreferenceStore();

        // Provider
        String providerName = store.getString(PreferenceConstants.LLM_PROVIDER);
        int providerIdx = 0;
        for (int i = 0; i < providers.length; i++) {
            if (providers[i].name().equals(providerName)) {
                providerIdx = i;
                break;
            }
        }
        providerCombo.select(providerIdx);

        // Populate models for selected provider
        LlmProviderConfig.Provider selectedProvider = providers[providerIdx];
        modelCombo.removeAll();
        for (String model : selectedProvider.getAvailableModels()) {
            modelCombo.add(model);
        }

        // Model
        String model = store.getString(PreferenceConstants.LLM_MODEL);
        if (model != null && !model.isEmpty()) {
            // Try to select it in the combo
            int modelIdx = modelCombo.indexOf(model);
            if (modelIdx >= 0) {
                modelCombo.select(modelIdx);
            } else {
                // Custom model - set as text
                modelCombo.setText(model);
            }
        } else {
            modelCombo.select(0);
        }

        // API Key
        String apiKey = store.getString(PreferenceConstants.LLM_API_KEY);
        apiKeyText.setText(apiKey != null ? apiKey : "");

        // Max Tokens
        int maxTokens = store.getInt(PreferenceConstants.LLM_MAX_TOKENS);
        maxTokensSpinner.setSelection(maxTokens > 0 ? maxTokens : 8192);

        // Include context
        includeContextCheck.setSelection(store.getBoolean(PreferenceConstants.INCLUDE_CONTEXT));
    }

    @Override
    public boolean performOk() {
        IPreferenceStore store = getPreferenceStore();

        int providerIdx = providerCombo.getSelectionIndex();
        if (providerIdx >= 0 && providerIdx < providers.length) {
            store.setValue(PreferenceConstants.LLM_PROVIDER, providers[providerIdx].name());
        }

        store.setValue(PreferenceConstants.LLM_API_KEY, apiKeyText.getText());
        store.setValue(PreferenceConstants.LLM_MODEL, modelCombo.getText());
        store.setValue(PreferenceConstants.LLM_MAX_TOKENS, maxTokensSpinner.getSelection());
        store.setValue(PreferenceConstants.INCLUDE_CONTEXT, includeContextCheck.getSelection());

        return super.performOk();
    }

    @Override
    protected void performDefaults() {
        IPreferenceStore store = getPreferenceStore();

        providerCombo.select(0); // Anthropic
        onProviderChanged();
        apiKeyText.setText("");
        maxTokensSpinner.setSelection(8192);
        includeContextCheck.setSelection(true);

        super.performDefaults();
    }
}
