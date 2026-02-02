package com.sap.ai.assistant.ui;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.ViewPart;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.sap.ai.assistant.Activator;
import com.sap.ai.assistant.agent.AgentCallback;
import com.sap.ai.assistant.agent.AgentLoop;
import com.sap.ai.assistant.agent.ContextBuilder;
import com.sap.ai.assistant.agent.ConversationManager;
import com.sap.ai.assistant.context.EditorContextTracker;
import com.sap.ai.assistant.llm.LlmProvider;
import com.sap.ai.assistant.llm.LlmProviderFactory;
import com.sap.ai.assistant.mcp.McpClient;
import com.sap.ai.assistant.mcp.McpServerConfig;
import com.sap.ai.assistant.mcp.McpToolAdapter;
import com.sap.ai.assistant.mcp.McpToolDefinitionParser;
import com.sap.ai.assistant.model.AdtContext;
import com.sap.ai.assistant.model.ChatConversation;
import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.DiffRequest;
import com.sap.ai.assistant.model.LlmProviderConfig;
import com.sap.ai.assistant.model.RequestLogEntry;
import com.sap.ai.assistant.model.SapSystemConnection;
import com.sap.ai.assistant.model.UsageTracker;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolDefinition;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.model.TransportSelection;
import com.sap.ai.assistant.model.TransportSelectionRequest;
import com.sap.ai.assistant.preferences.PreferenceConstants;
import com.sap.ai.assistant.sap.AdtCredentialProvider;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.tools.FindDefinitionTool;
import com.sap.ai.assistant.tools.GetSourceTool;
import com.sap.ai.assistant.tools.NodeContentsTool;
import com.sap.ai.assistant.tools.ObjectStructureTool;
import com.sap.ai.assistant.tools.ResearchTool;
import com.sap.ai.assistant.tools.SapTool;
import com.sap.ai.assistant.tools.SapToolRegistry;
import com.sap.ai.assistant.tools.SearchObjectTool;
import com.sap.ai.assistant.tools.TypeInfoTool;
import com.sap.ai.assistant.tools.UsageReferencesTool;

/**
 * The main Eclipse view for the SAP AI Assistant.
 * <p>
 * Combines a toolbar (SAP system selector, model label, preferences button),
 * a chat composite for messages, and orchestrates the agent loop on a
 * background Eclipse {@link Job}.
 * </p>
 */
public class AiAssistantView extends ViewPart {

    /** View ID as declared in {@code plugin.xml}. */
    public static final String VIEW_ID = "com.sap.ai.assistant.ui.AiAssistantView";

    // ---- Widgets ----
    private SystemSelectorComposite systemSelector;
    private Label modelLabel;
    private Button autoFixButton;
    private ChatComposite chatComposite;
    private DevLogComposite devLog;

    // ---- State ----
    private EditorContextTracker contextTracker;
    private ConversationManager conversationManager;
    private Job currentJob;
    private UsageTracker usageTracker;
    /** Session-level transport selection; {@code null} until first write op. */
    private TransportSelection sessionTransport;

    // ==================================================================
    // ViewPart lifecycle
    // ==================================================================

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        createToolbar(parent);
        createChatArea(parent);
        createDevLog(parent);
        registerEditorContextTracker();

        conversationManager = new ConversationManager();
        usageTracker = new UsageTracker();
        devLog.setTracker(usageTracker);
        updateModelLabel();
    }

    @Override
    public void setFocus() {
        if (chatComposite != null && !chatComposite.isDisposed()
                && chatComposite.getInputText() != null
                && !chatComposite.getInputText().isDisposed()) {
            chatComposite.getInputText().setFocus();
        }
    }

    @Override
    public void dispose() {
        // Cancel any running job
        if (currentJob != null) {
            currentJob.cancel();
            currentJob = null;
        }
        // Unregister part listener
        if (contextTracker != null) {
            try {
                IPartService ps = getSite().getWorkbenchWindow().getPartService();
                if (ps != null) {
                    ps.removePartListener(contextTracker);
                }
            } catch (Exception e) {
                // Workbench may already be shutting down
            }
            contextTracker = null;
        }
        super.dispose();
    }

    // ==================================================================
    // Widget creation
    // ==================================================================

    private void createToolbar(Composite parent) {
        Composite toolbar = new Composite(parent, SWT.NONE);
        GridLayout tl = new GridLayout(4, false);
        tl.marginWidth = 8;
        tl.marginHeight = 4;
        tl.horizontalSpacing = 8;
        toolbar.setLayout(tl);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // SAP system selector
        systemSelector = new SystemSelectorComposite(toolbar, SWT.NONE);
        systemSelector.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Model label
        modelLabel = new Label(toolbar, SWT.NONE);
        modelLabel.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY));
        modelLabel.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

        // Auto-Fix button
        autoFixButton = new Button(toolbar, SWT.PUSH);
        autoFixButton.setText("Auto-Fix");
        autoFixButton.setToolTipText("Fix all syntax errors and ATC findings in the current editor object");
        autoFixButton.addListener(SWT.Selection, e -> handleAutoFix());

        // Preferences button
        Button prefsButton = new Button(toolbar, SWT.PUSH);
        prefsButton.setText("Settings");
        prefsButton.addListener(SWT.Selection, e -> {
            PreferencesUtil.createPreferenceDialogOn(
                    getSite().getShell(),
                    "com.sap.ai.assistant.preferences.AiAssistantPreferencePage",
                    null, null).open();
            updateModelLabel();
        });

        // Separator below toolbar
        Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createChatArea(Composite parent) {
        chatComposite = new ChatComposite(parent, SWT.NONE);
        chatComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        chatComposite.setSendHandler(this::handleSend);
        chatComposite.setStopHandler(this::handleStop);
        chatComposite.setNewChatHandler(this::handleNewChat);
    }

    private void createDevLog(Composite parent) {
        devLog = new DevLogComposite(parent, SWT.NONE);
    }

    private void registerEditorContextTracker() {
        contextTracker = new EditorContextTracker();
        contextTracker.setOnContextChanged(() -> {
            Display.getDefault().asyncExec(() -> {
                updateAutoFixButton();
                refreshAvailableContexts();
            });
        });
        try {
            IPartService ps = getSite().getWorkbenchWindow().getPartService();
            if (ps != null) {
                ps.addPartListener(contextTracker);
            }
            // Initial population of available contexts
            Display.getDefault().asyncExec(() -> {
                refreshAvailableContexts();
                autoSelectActiveEditor();
            });
        } catch (Exception e) {
            System.err.println("AiAssistantView: could not register context tracker: " + e.getMessage());
        }
    }

    private void refreshAvailableContexts() {
        if (contextTracker == null || chatComposite == null || chatComposite.isDisposed()) return;
        List<AdtContext> all = contextTracker.getAllOpenContexts();
        chatComposite.setAvailableContexts(all);
    }

    private void autoSelectActiveEditor() {
        if (contextTracker == null || chatComposite == null || chatComposite.isDisposed()) return;
        AdtContext active = contextTracker.getCurrentContext();
        if (active != null && active.getObjectName() != null) {
            chatComposite.addSelectedContext(active);
        }
    }

    // ==================================================================
    // Public API for external handlers (commands, quick fixes)
    // ==================================================================

    /**
     * Programmatically sends a message as if the user typed it.
     * Called by keyboard shortcut handlers and quick-fix resolutions.
     */
    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        handleSend(text.trim());
    }

    // ==================================================================
    // Send / Stop / New Chat handlers
    // ==================================================================

    private void handleSend(String userText) {
        Display display = Display.getDefault();

        // Show user message immediately
        chatComposite.addUserMessage(userText);
        chatComposite.setRunning(true);

        // Disable system selector after first message
        systemSelector.setEnabled(false);

        // Gather configuration
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        String providerName    = store.getString(PreferenceConstants.LLM_PROVIDER);
        String apiKey          = store.getString(PreferenceConstants.LLM_API_KEY);
        String model           = store.getString(PreferenceConstants.LLM_MODEL);
        String researchModel   = store.getString(PreferenceConstants.RESEARCH_MODEL);
        int maxTokens          = store.getInt(PreferenceConstants.LLM_MAX_TOKENS);

        // Validate API key
        if (apiKey == null || apiKey.trim().isEmpty()) {
            chatComposite.addUserMessage("Error: No API key configured. Open Preferences to set one.");
            chatComposite.setRunning(false);
            return;
        }

        // Build LLM config
        LlmProviderConfig.Provider provider;
        try {
            provider = LlmProviderConfig.Provider.valueOf(providerName);
        } catch (Exception e) {
            provider = LlmProviderConfig.Provider.ANTHROPIC;
        }
        String baseUrl = store.getString(PreferenceConstants.LLM_BASE_URL);
        LlmProviderConfig config = new LlmProviderConfig(
                provider, apiKey, model, baseUrl, maxTokens > 0 ? maxTokens : 8192);

        // Build research sub-agent config (same provider/key, different model)
        String effectiveResearchModel = (researchModel != null && !researchModel.isEmpty())
                ? researchModel : model;
        LlmProviderConfig researchConfig = new LlmProviderConfig(
                provider, apiKey, effectiveResearchModel, baseUrl, maxTokens > 0 ? maxTokens : 8192);

        // Editor contexts from the dropdown selector
        List<AdtContext> selectedContexts = chatComposite.getSelectedContexts();

        // Selected SAP system — try ADT credentials before prompting for password
        SapSystemConnection selectedSystem = systemSelector.getSelectedSystem();
        AdtCredentialProvider.AdtSessionData adtSessionData = null;

        if (selectedSystem != null
                && (selectedSystem.getPassword() == null || selectedSystem.getPassword().isEmpty())) {

            boolean credentialsResolved = false;

            // Strategy 1: Try Eclipse Secure Storage (ADT saves passwords here)
            if (selectedSystem.hasAdtProject() && selectedSystem.getDestinationId() != null) {
                String storedPassword = AdtCredentialProvider.tryGetPasswordFromSecureStore(
                        selectedSystem.getDestinationId());
                if (storedPassword != null && !storedPassword.isEmpty()) {
                    selectedSystem.setPassword(storedPassword);
                    credentialsResolved = true;
                    System.out.println("AiAssistantView: using ADT stored credentials for "
                            + selectedSystem.getProjectName());
                }
            }

            // Strategy 2: Try extracting session cookies from ADT connection
            if (!credentialsResolved && selectedSystem.hasAdtProject()) {
                adtSessionData = AdtCredentialProvider.tryExtractSessionData(
                        selectedSystem.getAdtProject(), selectedSystem.getBaseUrl());
                if (adtSessionData != null) {
                    credentialsResolved = true;
                    System.out.println("AiAssistantView: using ADT session cookies for "
                            + selectedSystem.getProjectName());
                }
            }

            // Strategy 3: Fall back to password prompt
            if (!credentialsResolved) {
                org.eclipse.jface.dialogs.InputDialog passDialog =
                        new org.eclipse.jface.dialogs.InputDialog(
                                getSite().getShell(),
                                "SAP Login",
                                "Enter password for " + selectedSystem.getProjectName()
                                        + " (user: " + selectedSystem.getUser() + "):",
                                "", null) {
                            @Override
                            protected int getInputTextStyle() {
                                return SWT.SINGLE | SWT.BORDER | SWT.PASSWORD;
                            }
                        };
                if (passDialog.open() == org.eclipse.jface.window.Window.OK) {
                    selectedSystem.setPassword(passDialog.getValue());
                } else {
                    chatComposite.setRunning(false);
                    return;
                }
            }
        }

        // Determine conversation key from selected system
        String systemKey = selectedSystem != null
                ? selectedSystem.getProjectName()
                : "_default_";

        // Build system prompt with selected editor contexts
        String systemPrompt = (selectedContexts != null && !selectedContexts.isEmpty())
                ? ContextBuilder.buildSystemPrompt(selectedContexts, null)
                : null;

        // Prepare the conversation
        ChatConversation conversation = conversationManager.getOrCreate(systemKey,
                systemPrompt != null ? systemPrompt : "");
        if (systemPrompt != null) {
            conversation.setSystemPrompt(systemPrompt);
        }
        conversation.addUserMessage(userText);

        // Begin streaming message slot in the UI
        chatComposite.beginStreamingMessage();

        // Capture final refs for the job
        final LlmProviderConfig finalConfig = config;
        final LlmProviderConfig finalResearchConfig = researchConfig;
        final SapSystemConnection finalSystem = selectedSystem;
        final ChatConversation finalConversation = conversation;
        final List<AdtContext> finalEditorContexts = selectedContexts;
        final AdtCredentialProvider.AdtSessionData finalAdtSession = adtSessionData;

        // Read MCP server configs
        String mcpServersJson = store.getString(PreferenceConstants.MCP_SERVERS);
        final List<McpServerConfig> mcpConfigs = McpServerConfig.fromJson(mcpServersJson);

        // Capture token totals at turn start for per-turn usage display
        final int turnStartIn = usageTracker != null ? usageTracker.getTotalInputTokens() : 0;
        final int turnStartOut = usageTracker != null ? usageTracker.getTotalOutputTokens() : 0;

        // Run agent loop in background
        currentJob = new Job("SAP AI Assistant") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                AdtRestClient restClient = null;
                List<McpClient> mcpClients = new ArrayList<>();
                try {
                    // Create LLM provider
                    LlmProvider llmProvider = LlmProviderFactory.create(finalConfig);

                    // Connect to MCP servers and discover tools
                    List<SapTool> mcpTools = new ArrayList<>();
                    for (McpServerConfig mcpConfig : mcpConfigs) {
                        if (!mcpConfig.isEnabled()) continue;
                        try {
                            McpClient mcpClient = new McpClient(mcpConfig.getUrl());
                            mcpClient.connect();
                            mcpClients.add(mcpClient);

                            List<JsonObject> rawTools = mcpClient.listTools();
                            List<ToolDefinition> defs = McpToolDefinitionParser.parse(rawTools);

                            for (int i = 0; i < rawTools.size(); i++) {
                                String originalName = rawTools.get(i).get("name").getAsString();
                                mcpTools.add(new McpToolAdapter(mcpClient, originalName, defs.get(i)));
                            }
                            System.out.println("MCP: Loaded " + rawTools.size()
                                    + " tools from " + mcpConfig.getName());
                        } catch (Exception e) {
                            System.err.println("MCP: Failed to connect to "
                                    + mcpConfig.getName() + ": " + e.getMessage());
                        }
                    }

                    // Create SAP REST client
                    if (finalSystem != null) {
                        if (finalAdtSession != null) {
                            restClient = new AdtRestClient(
                                    finalSystem.getBaseUrl(),
                                    finalSystem.getUser(),
                                    finalSystem.getClient(),
                                    "EN",
                                    false,
                                    finalAdtSession.getCookieManager(),
                                    finalAdtSession.getCsrfToken());
                        } else {
                            restClient = new AdtRestClient(
                                    finalSystem.getBaseUrl(),
                                    finalSystem.getUser(),
                                    finalSystem.getPassword(),
                                    finalSystem.getClient(),
                                    "EN",
                                    false);
                        }
                        restClient.login();
                    }

                    // Build research sub-agent (SAP read tools + MCP tools)
                    boolean hasResearchTool = false;
                    List<SapTool> mainAdditionalTools = new ArrayList<>();

                    // Collect SAP read-only tools for the research sub-agent
                    List<SapTool> researchTools = new ArrayList<>();
                    if (restClient != null) {
                        researchTools.add(new SearchObjectTool(restClient));
                        researchTools.add(new GetSourceTool(restClient));
                        researchTools.add(new ObjectStructureTool(restClient));
                        researchTools.add(new NodeContentsTool(restClient));
                        researchTools.add(new FindDefinitionTool(restClient));
                        researchTools.add(new UsageReferencesTool(restClient));
                        researchTools.add(new TypeInfoTool(restClient));
                    }
                    researchTools.addAll(mcpTools);

                    if (!researchTools.isEmpty()) {
                        // Create research LLM provider (same provider, potentially different model)
                        LlmProvider researchLlmProvider = LlmProviderFactory.create(finalResearchConfig);
                        SapToolRegistry researchRegistry = SapToolRegistry.withToolsOnly(researchTools);
                        ResearchTool researchTool = new ResearchTool(researchLlmProvider, researchRegistry);
                        mainAdditionalTools.add(researchTool);
                        hasResearchTool = true;
                    }

                    // Build main tool registry (all SAP tools + research tool)
                    SapToolRegistry toolRegistry = null;
                    if (finalSystem != null) {
                        toolRegistry = new SapToolRegistry(restClient, mainAdditionalTools);
                    } else if (!mainAdditionalTools.isEmpty()) {
                        toolRegistry = SapToolRegistry.withToolsOnly(mainAdditionalTools);
                    }

                    // Rebuild system prompt with research tool awareness + transport info
                    String updatedPrompt = ContextBuilder.buildSystemPrompt(
                            finalEditorContexts, toolRegistry, hasResearchTool,
                            sessionTransport);
                    finalConversation.setSystemPrompt(updatedPrompt);

                    // Create and run agent loop (pass restClient for diff preview)
                    AgentLoop agent = new AgentLoop(llmProvider, toolRegistry, restClient, finalConfig);
                    agent.setSessionTransport(sessionTransport);

                    agent.run(finalConversation, new AgentCallback() {

                        @Override
                        public void onTextToken(String token) {
                            if (monitor.isCanceled()) return;
                            display.asyncExec(() -> chatComposite.appendStreamToken(token));
                        }

                        @Override
                        public void onToolCallStart(ToolCall toolCall) {
                            if (monitor.isCanceled()) return;
                            display.asyncExec(() -> chatComposite.addToolCallWidget(toolCall));
                        }

                        @Override
                        public void onToolCallEnd(ToolResult result) {
                            if (monitor.isCanceled()) return;
                            display.asyncExec(() -> chatComposite.updateToolCallResult(result));
                        }

                        @Override
                        public void onDiffApprovalNeeded(DiffRequest diffRequest) {
                            if (monitor.isCanceled()) {
                                diffRequest.setDecision(DiffRequest.Decision.REJECTED);
                                return;
                            }
                            // Show diff widget in UI; agent thread blocks on awaitDecision()
                            display.asyncExec(() -> chatComposite.addDiffPreview(diffRequest));
                        }

                        @Override
                        public void onTransportSelectionNeeded(TransportSelectionRequest request) {
                            if (monitor.isCanceled()) {
                                request.setSelection(TransportSelection.local());
                                return;
                            }
                            display.asyncExec(() -> {
                                TransportSelectionDialog dialog = new TransportSelectionDialog(
                                        chatComposite.getShell(),
                                        request.getAvailableTransports());
                                if (dialog.open() == org.eclipse.jface.window.Window.OK
                                        && dialog.getResult() != null) {
                                    request.setSelection(dialog.getResult());
                                } else {
                                    request.setSelection(TransportSelection.local());
                                }
                            });
                        }

                        @Override
                        public void onComplete(ChatMessage finalMessage) {
                            display.asyncExec(() -> {
                                chatComposite.finishStreamingMessage();
                                // Show per-turn token usage
                                if (usageTracker != null) {
                                    int turnIn = usageTracker.getTotalInputTokens() - turnStartIn;
                                    int turnOut = usageTracker.getTotalOutputTokens() - turnStartOut;
                                    if (turnIn > 0 || turnOut > 0) {
                                        chatComposite.showTokenUsage(
                                            formatTokenCount(turnIn) + " in / "
                                            + formatTokenCount(turnOut) + " out");
                                    }
                                }
                                chatComposite.setRunning(false);
                                updateAutoFixButton();
                                refreshOpenEditors();
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            display.asyncExec(() -> {
                                chatComposite.finishStreamingMessage();
                                chatComposite.addUserMessage(
                                        "Error: " + error.getMessage());
                                chatComposite.setRunning(false);
                                updateAutoFixButton();
                            });
                        }

                        @Override
                        public void onRequestComplete(RequestLogEntry entry) {
                            usageTracker.addEntry(entry);
                            display.asyncExec(() -> {
                                if (devLog != null && !devLog.isDisposed()) {
                                    devLog.addEntry(entry);
                                }
                            });
                        }
                    });

                    // Persist transport selection for subsequent messages
                    sessionTransport = agent.getSessionTransport();

                    if (monitor.isCanceled()) {
                        return Status.CANCEL_STATUS;
                    }
                    return Status.OK_STATUS;

                } catch (Exception e) {
                    String errorDetail = e.getMessage();
                    if (errorDetail == null || errorDetail.isEmpty()) {
                        errorDetail = e.getClass().getSimpleName();
                    }
                    if (e instanceof java.net.ConnectException) {
                        String causeMsg = e.getCause() != null ? e.getCause().toString() : "";
                        errorDetail = "Connection failed: " + e.getMessage()
                                + (causeMsg.isEmpty() ? "" : " (" + causeMsg + ")")
                                + ". Check your network connection and SAP system URL.";
                    }
                    final String msg = errorDetail;
                    display.asyncExec(() -> {
                        chatComposite.finishStreamingMessage();
                        chatComposite.addUserMessage("Error: " + msg);
                        chatComposite.setRunning(false);
                    });
                    return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
                            "Agent loop failed: " + msg, e);
                } finally {
                    if (restClient != null) {
                        try {
                            restClient.logout();
                        } catch (Exception ignored) {
                            // Best-effort logout
                        }
                    }
                    for (McpClient mc : mcpClients) {
                        try {
                            mc.disconnect();
                        } catch (Exception ignored) {
                            // Best-effort disconnect
                        }
                    }
                }
            }
        };
        currentJob.setUser(false);
        currentJob.schedule();
    }

    private void handleStop() {
        if (currentJob != null) {
            currentJob.cancel();
            currentJob = null;
        }
        chatComposite.finishStreamingMessage();
        chatComposite.setRunning(false);
    }

    private void handleNewChat() {
        if (currentJob != null) {
            currentJob.cancel();
            currentJob = null;
        }
        chatComposite.clearMessages();
        chatComposite.setRunning(false);
        chatComposite.clearContextSelections();
        systemSelector.setEnabled(true);
        sessionTransport = null;

        if (usageTracker != null) {
            usageTracker.clear();
        }
        if (devLog != null && !devLog.isDisposed()) {
            devLog.clearLog();
        }

        if (conversationManager != null) {
            conversationManager = new ConversationManager();
        }

        // Re-populate available contexts and auto-select active editor
        refreshAvailableContexts();
        autoSelectActiveEditor();
    }

    // ==================================================================
    // Auto-Fix
    // ==================================================================

    private void handleAutoFix() {
        AdtContext context = (contextTracker != null) ? contextTracker.getCurrentContext() : null;

        if (context == null || context.getObjectName() == null || context.getObjectName().isEmpty()) {
            chatComposite.addUserMessage(
                    "Error: No ABAP object is currently open in the editor. "
                    + "Please open an ABAP object first.");
            return;
        }

        String prompt = buildAutoFixPrompt(context);
        handleSend(prompt);
    }

    private String buildAutoFixPrompt(AdtContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("AUTO-FIX REQUEST: Please fix all errors and issues in the ABAP object \"");
        prompt.append(context.getObjectName());
        prompt.append("\".\n\n");

        prompt.append("Please follow this workflow:\n");
        prompt.append("1. Read the current source code using sap_get_source\n");
        prompt.append("2. Run a syntax check using sap_syntax_check\n");
        prompt.append("3. Run ATC checks using sap_atc_run\n");
        prompt.append("4. Analyze ALL errors, warnings, and ATC findings\n");
        prompt.append("5. Fix all issues in the source code\n");
        prompt.append("6. Write the corrected source code using sap_write_and_check\n");
        prompt.append("7. Verify the fix by running syntax check again\n");
        prompt.append("8. If errors remain, iterate until all issues are resolved\n\n");

        List<String> errors = context.getErrors();
        if (errors != null && !errors.isEmpty()) {
            prompt.append("Known errors/warnings from the editor:\n");
            for (String error : errors) {
                prompt.append("- ").append(error).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("Fix everything and ensure the object compiles cleanly with no ATC findings.");
        return prompt.toString();
    }

    private void updateAutoFixButton() {
        if (autoFixButton == null || autoFixButton.isDisposed()) return;
        boolean isRunning = currentJob != null
                && currentJob.getState() == Job.RUNNING;
        autoFixButton.setEnabled(!isRunning);
    }

    // ==================================================================
    // Editor refresh
    // ==================================================================

    /**
     * Refreshes all open editors by resetting their document providers.
     * This picks up changes written to the SAP system by the agent,
     * equivalent to the user pressing F5 in the editor.
     */
    private void refreshOpenEditors() {
        try {
            org.eclipse.ui.IWorkbenchPage page = getSite().getWorkbenchWindow().getActivePage();
            if (page == null) return;
            for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
                org.eclipse.ui.IEditorPart editor = ref.getEditor(false);
                if (editor == null) continue;
                try {
                    // Try resource refresh (works for ADT project-backed editors)
                    org.eclipse.core.resources.IResource resource =
                            editor.getEditorInput().getAdapter(org.eclipse.core.resources.IResource.class);
                    if (resource != null && resource.exists()) {
                        resource.refreshLocal(
                                org.eclipse.core.resources.IResource.DEPTH_ZERO, null);
                    }
                } catch (Exception e) {
                    // Ignore per-editor refresh failures
                }
            }
        } catch (Exception e) {
            System.err.println("AiAssistantView: editor refresh failed: " + e.getMessage());
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private String formatTokenCount(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1000000) return String.format("%.0fK", n / 1000.0);
        return String.format("%.1fM", n / 1000000.0);
    }

    private void updateModelLabel() {
        if (modelLabel == null || modelLabel.isDisposed()) return;
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        String providerName = store.getString(PreferenceConstants.LLM_PROVIDER);
        String model = store.getString(PreferenceConstants.LLM_MODEL);

        String display;
        try {
            LlmProviderConfig.Provider p = LlmProviderConfig.Provider.valueOf(providerName);
            display = p.getDisplayName();
            if (model != null && !model.isEmpty()) {
                display += " / " + model;
            } else {
                display += " / " + p.getDefaultModel();
            }
        } catch (Exception e) {
            display = providerName;
        }
        modelLabel.setText(display);
        modelLabel.requestLayout();
    }
}
