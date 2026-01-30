package com.sap.ai.assistant.preferences;

import java.util.ArrayList;
import java.util.List;

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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.sap.ai.assistant.Activator;
import com.sap.ai.assistant.mcp.McpClient;
import com.sap.ai.assistant.mcp.McpServerConfig;
import com.sap.ai.assistant.model.LlmProviderConfig;
import com.sap.ai.assistant.model.SavedSapSystem;

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
    private Text baseUrlText;
    private Label baseUrlLabel;
    private Combo modelCombo;
    private Spinner maxTokensSpinner;
    private Button includeContextCheck;
    private Table sapSystemsTable;
    private List<SavedSapSystem> savedSapSystems = new ArrayList<>();
    private Table mcpTable;
    private List<McpServerConfig> mcpServers = new ArrayList<>();

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

        // Base URL (visible for all providers, editable only for Custom)
        baseUrlLabel = new Label(llmGroup, SWT.NONE);
        baseUrlLabel.setText("Base URL:");
        baseUrlText = new Text(llmGroup, SWT.BORDER);
        baseUrlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        baseUrlText.setMessage("https://api.example.com");

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

        // -- Saved SAP Systems group --
        Group sapGroup = new Group(container, SWT.NONE);
        sapGroup.setText("Saved SAP Systems");
        sapGroup.setLayout(new GridLayout(2, false));
        sapGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        sapSystemsTable = new Table(sapGroup, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        sapSystemsTable.setHeaderVisible(true);
        sapSystemsTable.setLinesVisible(true);
        GridData sapTableGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        sapTableGd.heightHint = 80;
        sapSystemsTable.setLayoutData(sapTableGd);

        TableColumn hostCol = new TableColumn(sapSystemsTable, SWT.NONE);
        hostCol.setText("Host");
        hostCol.setWidth(180);

        TableColumn portCol = new TableColumn(sapSystemsTable, SWT.NONE);
        portCol.setText("Port");
        portCol.setWidth(60);

        TableColumn clientCol = new TableColumn(sapSystemsTable, SWT.NONE);
        clientCol.setText("Client");
        clientCol.setWidth(60);

        TableColumn userCol = new TableColumn(sapSystemsTable, SWT.NONE);
        userCol.setText("User");
        userCol.setWidth(100);

        TableColumn sslCol = new TableColumn(sapSystemsTable, SWT.NONE);
        sslCol.setText("SSL");
        sslCol.setWidth(40);

        Composite sapButtons = new Composite(sapGroup, SWT.NONE);
        sapButtons.setLayout(new GridLayout(1, true));
        sapButtons.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

        Button sapAddBtn = new Button(sapButtons, SWT.PUSH);
        sapAddBtn.setText("Add...");
        sapAddBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sapAddBtn.addListener(SWT.Selection, e -> handleSapSystemAdd());

        Button sapRemoveBtn = new Button(sapButtons, SWT.PUSH);
        sapRemoveBtn.setText("Remove");
        sapRemoveBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sapRemoveBtn.addListener(SWT.Selection, e -> handleSapSystemRemove());

        // -- MCP Servers group --
        Group mcpGroup = new Group(container, SWT.NONE);
        mcpGroup.setText("MCP Documentation Servers");
        mcpGroup.setLayout(new GridLayout(2, false));
        mcpGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        mcpTable = new Table(mcpGroup, SWT.BORDER | SWT.CHECK | SWT.FULL_SELECTION | SWT.SINGLE);
        mcpTable.setHeaderVisible(true);
        mcpTable.setLinesVisible(true);
        GridData tableGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableGd.heightHint = 100;
        mcpTable.setLayoutData(tableGd);

        TableColumn nameCol = new TableColumn(mcpTable, SWT.NONE);
        nameCol.setText("Name");
        nameCol.setWidth(120);

        TableColumn urlCol = new TableColumn(mcpTable, SWT.NONE);
        urlCol.setText("URL");
        urlCol.setWidth(350);

        // Buttons for MCP table
        Composite mcpButtons = new Composite(mcpGroup, SWT.NONE);
        mcpButtons.setLayout(new GridLayout(1, true));
        mcpButtons.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

        Button addBtn = new Button(mcpButtons, SWT.PUSH);
        addBtn.setText("Add...");
        addBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        addBtn.addListener(SWT.Selection, e -> handleMcpAdd());

        Button removeBtn = new Button(mcpButtons, SWT.PUSH);
        removeBtn.setText("Remove");
        removeBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        removeBtn.addListener(SWT.Selection, e -> handleMcpRemove());

        Button testBtn = new Button(mcpButtons, SWT.PUSH);
        testBtn.setText("Test");
        testBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        testBtn.addListener(SWT.Selection, e -> handleMcpTest());

        // Load current values
        loadValues();

        return container;
    }

    private void onProviderChanged() {
        int idx = providerCombo.getSelectionIndex();
        if (idx < 0 || idx >= providers.length) return;

        LlmProviderConfig.Provider provider = providers[idx];
        String currentModel = modelCombo.getText();
        boolean isCustom = provider == LlmProviderConfig.Provider.CUSTOM;

        // Base URL: editable for Custom, show default for built-in
        if (isCustom) {
            baseUrlText.setEnabled(true);
            if (baseUrlText.getText().isEmpty()) {
                baseUrlText.setText("");
            }
        } else {
            baseUrlText.setEnabled(false);
            baseUrlText.setText(provider.getDefaultBaseUrl());
        }

        // Update model dropdown with this provider's models
        modelCombo.removeAll();
        for (String model : provider.getAvailableModels()) {
            modelCombo.add(model);
        }

        if (isCustom) {
            // Custom provider: user types model name freely
            modelCombo.setText(currentModel);
        } else {
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

        // Base URL
        String baseUrl = store.getString(PreferenceConstants.LLM_BASE_URL);
        boolean isCustom = selectedProvider == LlmProviderConfig.Provider.CUSTOM;
        if (isCustom) {
            baseUrlText.setEnabled(true);
            baseUrlText.setText(baseUrl != null ? baseUrl : "");
        } else {
            baseUrlText.setEnabled(false);
            baseUrlText.setText(selectedProvider.getDefaultBaseUrl());
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
        } else if (modelCombo.getItemCount() > 0) {
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

        // Saved SAP Systems
        String sapJson = store.getString(PreferenceConstants.SAP_SAVED_SYSTEMS);
        savedSapSystems = SavedSapSystem.fromJson(sapJson);
        refreshSapSystemsTable();

        // MCP Servers
        String mcpJson = store.getString(PreferenceConstants.MCP_SERVERS);
        mcpServers = McpServerConfig.fromJson(mcpJson);
        refreshMcpTable();
    }

    private void refreshSapSystemsTable() {
        sapSystemsTable.removeAll();
        for (SavedSapSystem sys : savedSapSystems) {
            TableItem item = new TableItem(sapSystemsTable, SWT.NONE);
            item.setText(0, sys.getHost());
            item.setText(1, String.valueOf(sys.getPort()));
            item.setText(2, sys.getClient());
            item.setText(3, sys.getUser() != null ? sys.getUser() : "");
            item.setText(4, sys.isUseSsl() ? "Yes" : "No");
        }
    }

    private void refreshMcpTable() {
        mcpTable.removeAll();
        for (McpServerConfig cfg : mcpServers) {
            TableItem item = new TableItem(mcpTable, SWT.NONE);
            item.setChecked(cfg.isEnabled());
            item.setText(0, cfg.getName());
            item.setText(1, cfg.getUrl());
        }
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
        store.setValue(PreferenceConstants.LLM_BASE_URL, baseUrlText.getText());
        store.setValue(PreferenceConstants.LLM_MAX_TOKENS, maxTokensSpinner.getSelection());
        store.setValue(PreferenceConstants.INCLUDE_CONTEXT, includeContextCheck.getSelection());

        // Sync enabled state from table checkboxes
        for (int i = 0; i < mcpServers.size() && i < mcpTable.getItemCount(); i++) {
            mcpServers.get(i).setEnabled(mcpTable.getItem(i).getChecked());
        }
        store.setValue(PreferenceConstants.MCP_SERVERS, McpServerConfig.toJson(mcpServers));

        // Saved SAP Systems
        store.setValue(PreferenceConstants.SAP_SAVED_SYSTEMS, SavedSapSystem.toJson(savedSapSystems));

        return super.performOk();
    }

    @Override
    protected void performDefaults() {
        providerCombo.select(0); // Anthropic
        onProviderChanged();
        apiKeyText.setText("");
        baseUrlText.setText("");
        maxTokensSpinner.setSelection(8192);
        includeContextCheck.setSelection(true);

        // Reset saved SAP systems
        savedSapSystems = new ArrayList<>();
        refreshSapSystemsTable();

        // Reset MCP servers to default
        mcpServers = new ArrayList<>();
        mcpServers.add(new McpServerConfig("SAP Docs",
                "https://mcp-sap-docs.marianzeis.de/mcp", true));
        refreshMcpTable();

        super.performDefaults();
    }

    // -- SAP System management -----------------------------------------------

    private void handleSapSystemAdd() {
        AddSapSystemDialog dlg = new AddSapSystemDialog(getShell());
        if (dlg.open() != org.eclipse.jface.window.Window.OK) return;

        SavedSapSystem sys = dlg.getResult();
        if (sys != null) {
            savedSapSystems.add(sys);
            refreshSapSystemsTable();
        }
    }

    /**
     * Single dialog to add a SAP system — all fields on one screen.
     */
    private static class AddSapSystemDialog extends Dialog {

        private Text urlText;
        private Text clientText;
        private Text userText;

        private SavedSapSystem result;

        protected AddSapSystemDialog(Shell parentShell) {
            super(parentShell);
        }

        @Override
        protected void configureShell(Shell newShell) {
            super.configureShell(newShell);
            newShell.setText("Add SAP System");
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite area = (Composite) super.createDialogArea(parent);

            Composite container = new Composite(area, SWT.NONE);
            container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            GridLayout gl = new GridLayout(2, false);
            gl.marginWidth = 10;
            gl.marginHeight = 10;
            container.setLayout(gl);

            // URL
            Label urlLabel = new Label(container, SWT.NONE);
            urlLabel.setText("System URL:");
            urlText = new Text(container, SWT.BORDER);
            urlText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            urlText.setMessage("http://hostname:8000");

            // Hint
            new Label(container, SWT.NONE); // spacer
            Label hint = new Label(container, SWT.NONE);
            hint.setText("e.g. http://myhost.corp:8000  or  https://myhost:44300");
            hint.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));

            // Client
            Label clientLabel = new Label(container, SWT.NONE);
            clientLabel.setText("Client:");
            clientText = new Text(container, SWT.BORDER);
            clientText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            clientText.setText("100");

            // User
            Label userLabel = new Label(container, SWT.NONE);
            userLabel.setText("User:");
            userText = new Text(container, SWT.BORDER);
            userText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            return area;
        }

        @Override
        protected void createButtonsForButtonBar(Composite parent) {
            createButton(parent, IDialogConstants.OK_ID, "Add", true);
            createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        }

        @Override
        protected void okPressed() {
            String urlInput = urlText.getText().trim();
            String client = clientText.getText().trim();
            String user = userText.getText().trim();

            if (urlInput.isEmpty()) {
                setErrorMessage("URL is required");
                return;
            }

            // Parse the URL
            String host = urlInput;
            int port = 8000;
            boolean useSsl = false;

            if (host.startsWith("https://")) {
                useSsl = true;
                host = host.substring(8);
            } else if (host.startsWith("http://")) {
                useSsl = false;
                host = host.substring(7);
            }

            // Strip trailing slash/path
            int slashIdx = host.indexOf('/');
            if (slashIdx >= 0) {
                host = host.substring(0, slashIdx);
            }

            // Extract port from host:port
            int colonIdx = host.lastIndexOf(':');
            if (colonIdx >= 0) {
                try {
                    port = Integer.parseInt(host.substring(colonIdx + 1));
                    host = host.substring(0, colonIdx);
                } catch (NumberFormatException ignored) {
                    // Not a port — keep host as-is, use default port
                }
            }

            // If no explicit protocol, infer SSL from port
            if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                useSsl = (port == 443 || port == 8443
                        || (port >= 44300 && port <= 44399));
            }

            if (host.isEmpty()) {
                setErrorMessage("Invalid URL");
                return;
            }

            if (client.isEmpty()) {
                client = "100";
            }

            result = new SavedSapSystem(host, port, client, user, useSsl);
            super.okPressed();
        }

        private void setErrorMessage(String msg) {
            org.eclipse.jface.dialogs.MessageDialog.openError(getShell(), "Add SAP System", msg);
        }

        public SavedSapSystem getResult() {
            return result;
        }
    }

    private void handleSapSystemRemove() {
        int idx = sapSystemsTable.getSelectionIndex();
        if (idx < 0 || idx >= savedSapSystems.size()) return;

        savedSapSystems.remove(idx);
        refreshSapSystemsTable();
    }

    // -- MCP Server management -----------------------------------------------

    private void handleMcpAdd() {
        org.eclipse.jface.dialogs.InputDialog nameDialog =
                new org.eclipse.jface.dialogs.InputDialog(
                        getShell(), "Add MCP Server", "Server name:", "", null);
        if (nameDialog.open() != org.eclipse.jface.window.Window.OK) return;
        String name = nameDialog.getValue().trim();
        if (name.isEmpty()) return;

        org.eclipse.jface.dialogs.InputDialog urlDialog =
                new org.eclipse.jface.dialogs.InputDialog(
                        getShell(), "Add MCP Server",
                        "Server URL (e.g. https://example.com/mcp):", "", null);
        if (urlDialog.open() != org.eclipse.jface.window.Window.OK) return;
        String url = urlDialog.getValue().trim();
        if (url.isEmpty()) return;

        mcpServers.add(new McpServerConfig(name, url, true));
        refreshMcpTable();
    }

    private void handleMcpRemove() {
        int idx = mcpTable.getSelectionIndex();
        if (idx < 0 || idx >= mcpServers.size()) return;

        mcpServers.remove(idx);
        refreshMcpTable();
    }

    private void handleMcpTest() {
        int idx = mcpTable.getSelectionIndex();
        if (idx < 0 || idx >= mcpServers.size()) return;

        McpServerConfig cfg = mcpServers.get(idx);
        try {
            McpClient client = new McpClient(cfg.getUrl());
            client.connect();
            int toolCount = client.listTools().size();
            client.disconnect();

            org.eclipse.jface.dialogs.MessageDialog.openInformation(
                    getShell(), "MCP Connection Test",
                    "Connected successfully to \"" + cfg.getName() + "\".\n"
                    + toolCount + " tools available.");
        } catch (Exception e) {
            org.eclipse.jface.dialogs.MessageDialog.openError(
                    getShell(), "MCP Connection Test",
                    "Failed to connect to \"" + cfg.getName() + "\":\n" + e.getMessage());
        }
    }
}
