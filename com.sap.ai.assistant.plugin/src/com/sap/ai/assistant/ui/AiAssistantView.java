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

import com.sap.ai.assistant.Activator;
import com.sap.ai.assistant.agent.AgentCallback;
import com.sap.ai.assistant.agent.AgentLoop;
import com.sap.ai.assistant.agent.ContextBuilder;
import com.sap.ai.assistant.agent.ConversationManager;
import com.sap.ai.assistant.context.EditorContextTracker;
import com.sap.ai.assistant.llm.LlmProvider;
import com.sap.ai.assistant.llm.LlmProviderFactory;
import com.sap.ai.assistant.model.AdtContext;
import com.sap.ai.assistant.model.ChatConversation;
import com.sap.ai.assistant.model.ChatMessage;
import com.sap.ai.assistant.model.LlmProviderConfig;
import com.sap.ai.assistant.model.SapSystemConnection;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolResult;
import com.sap.ai.assistant.preferences.PreferenceConstants;
import com.sap.ai.assistant.sap.AdtRestClient;
import com.sap.ai.assistant.tools.SapToolRegistry;

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
    private ChatComposite chatComposite;

    // ---- State ----
    private EditorContextTracker contextTracker;
    private ConversationManager conversationManager;
    private Job currentJob;

    // ==================================================================
    // ViewPart lifecycle
    // ==================================================================

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        createToolbar(parent);
        createChatArea(parent);
        registerEditorContextTracker();

        conversationManager = new ConversationManager();
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
        GridLayout tl = new GridLayout(3, false);
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

    private void registerEditorContextTracker() {
        contextTracker = new EditorContextTracker();
        try {
            IPartService ps = getSite().getWorkbenchWindow().getPartService();
            if (ps != null) {
                ps.addPartListener(contextTracker);
            }
        } catch (Exception e) {
            System.err.println("AiAssistantView: could not register context tracker: " + e.getMessage());
        }
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
        String providerName = store.getString(PreferenceConstants.LLM_PROVIDER);
        String apiKey       = store.getString(PreferenceConstants.LLM_API_KEY);
        String model        = store.getString(PreferenceConstants.LLM_MODEL);
        int maxTokens       = store.getInt(PreferenceConstants.LLM_MAX_TOKENS);
        boolean includeCtx  = store.getBoolean(PreferenceConstants.INCLUDE_CONTEXT);

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
        LlmProviderConfig config = new LlmProviderConfig(
                provider, apiKey, model, null, maxTokens > 0 ? maxTokens : 8192);

        // Editor context
        AdtContext editorContext = null;
        if (includeCtx && contextTracker != null) {
            editorContext = contextTracker.getCurrentContext();
        }

        // Update context label
        if (editorContext != null && editorContext.getObjectName() != null) {
            String ctxInfo = editorContext.getObjectName();
            if (editorContext.getObjectType() != null) {
                ctxInfo += " [" + editorContext.getObjectType() + "]";
            }
            if (editorContext.getCursorLine() > 0) {
                ctxInfo += " line " + editorContext.getCursorLine();
            }
            chatComposite.setContextLabel("Context: " + ctxInfo);
        }

        // Selected SAP system — prompt for password if missing
        SapSystemConnection selectedSystem = systemSelector.getSelectedSystem();
        if (selectedSystem != null
                && (selectedSystem.getPassword() == null || selectedSystem.getPassword().isEmpty())) {
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

        // Determine conversation key from selected system
        String systemKey = selectedSystem != null
                ? selectedSystem.getProjectName()
                : "_default_";

        // Build system prompt with editor context
        String systemPrompt = editorContext != null
                ? ContextBuilder.buildSystemPrompt(editorContext)
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
        final SapSystemConnection finalSystem = selectedSystem;
        final ChatConversation finalConversation = conversation;

        // Run agent loop in background
        currentJob = new Job("SAP AI Assistant") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                AdtRestClient restClient = null;
                try {
                    // Create LLM provider
                    LlmProvider llmProvider = LlmProviderFactory.create(finalConfig);

                    // Create SAP REST client if system selected
                    SapToolRegistry toolRegistry = null;
                    if (finalSystem != null) {
                        restClient = new AdtRestClient(
                                finalSystem.getBaseUrl(),
                                finalSystem.getUser(),
                                finalSystem.getPassword(),
                                finalSystem.getClient(),
                                "EN",
                                false);
                        restClient.login();
                        toolRegistry = new SapToolRegistry(restClient);
                    }

                    // Create and run agent loop
                    AgentLoop agent = new AgentLoop(llmProvider, toolRegistry);

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
                        public void onComplete(ChatMessage finalMessage) {
                            display.asyncExec(() -> {
                                chatComposite.finishStreamingMessage();
                                chatComposite.setRunning(false);
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            display.asyncExec(() -> {
                                chatComposite.finishStreamingMessage();
                                chatComposite.addUserMessage(
                                        "Error: " + error.getMessage());
                                chatComposite.setRunning(false);
                            });
                        }
                    });

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
                        errorDetail = "Connection failed. Check your network and API key settings.";
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
        chatComposite.setContextLabel("");
        systemSelector.setEnabled(true);

        if (conversationManager != null) {
            conversationManager = new ConversationManager();
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

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
