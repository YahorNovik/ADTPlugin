package com.sap.ai.assistant.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.sap.ai.assistant.context.AdtEditorHelper;
import com.sap.ai.assistant.model.AdtContext;
import com.sap.ai.assistant.model.DiffRequest;
import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolResult;

/**
 * The main chat composite that houses the scrollable message area, context
 * label, text input, and action buttons (Send, Stop, New Chat).
 * <p>
 * External code interacts through callback setters and the public methods
 * for adding messages, streaming tokens, and managing tool-call widgets.
 * </p>
 */
public class ChatComposite extends Composite {

    // ---- Children ----
    private ScrolledComposite scrolledComposite;
    private Composite messagesContainer;
    private ContextSelectorComposite contextSelector;
    private StyledText inputText;
    private Button sendButton;
    private Button stopButton;
    private Button newChatButton;

    // ---- State ----
    private MessageRenderer messageRenderer;
    private StyledText currentStreamingText;
    private ToolCallWidget lastToolCallWidget;
    private MentionPopup mentionPopup;

    // ---- Callbacks ----
    private Consumer<String> sendHandler;
    private Runnable stopHandler;
    private Runnable newChatHandler;

    /**
     * Create the chat composite.
     *
     * @param parent the parent composite
     * @param style  SWT style bits
     */
    public ChatComposite(Composite parent, int style) {
        super(parent, style);
        this.messageRenderer = new MessageRenderer(this, getDisplay());

        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 0;
        mainLayout.marginHeight = 0;
        setLayout(mainLayout);

        createMessagesArea();
        createContextBar();
        createInputArea();
    }

    // ==================================================================
    // Callback setters
    // ==================================================================

    /**
     * Set the handler invoked when the user presses Send or Enter.
     *
     * @param handler receives the trimmed user input text
     */
    public void setSendHandler(Consumer<String> handler) {
        this.sendHandler = handler;
    }

    /**
     * Set the handler invoked when the user presses Stop.
     */
    public void setStopHandler(Runnable handler) {
        this.stopHandler = handler;
    }

    /**
     * Set the handler invoked when the user presses New Chat.
     */
    public void setNewChatHandler(Runnable handler) {
        this.newChatHandler = handler;
    }

    // ==================================================================
    // Public API -- message management
    // ==================================================================

    /**
     * Add a user message bubble to the message area.
     *
     * @param text the user's message text
     */
    public void addUserMessage(String text) {
        if (isDisposed()) return;
        messageRenderer.createUserMessage(messagesContainer, text);
        layoutAndScroll();
    }

    /**
     * Begin a new streaming assistant message. Subsequent calls to
     * {@link #appendStreamToken(String)} will append to this widget.
     */
    public void beginStreamingMessage() {
        if (isDisposed()) return;
        currentStreamingText = messageRenderer.createAssistantMessage(messagesContainer);
        layoutAndScroll();
    }

    /**
     * Append a token to the current streaming assistant message.
     *
     * @param token the text token to append
     */
    public void appendStreamToken(String token) {
        if (isDisposed() || currentStreamingText == null || currentStreamingText.isDisposed()) {
            return;
        }
        currentStreamingText.append(token);
        scrollToBottom();
    }

    /**
     * Finalise the current streaming message by applying Markdown styling.
     */
    public void finishStreamingMessage() {
        if (currentStreamingText != null && !currentStreamingText.isDisposed()) {
            MarkdownRenderer.applyMarkdownStyling(currentStreamingText);
        }
        currentStreamingText = null;
        layoutAndScroll();
    }

    /**
     * Show a compact token usage label below the last assistant message.
     *
     * @param text token usage summary (e.g. "1,234 in / 567 out")
     */
    public void showTokenUsage(String text) {
        if (isDisposed() || messagesContainer == null || messagesContainer.isDisposed()) return;
        Label usageLabel = new Label(messagesContainer, SWT.NONE);
        usageLabel.setText(text);
        usageLabel.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
        GridData gd = new GridData(SWT.END, SWT.CENTER, true, false);
        gd.horizontalIndent = 8;
        usageLabel.setLayoutData(gd);
        layoutAndScroll();
    }

    /**
     * Add a collapsible tool-call widget to the message area.
     *
     * @param call the tool call being executed
     */
    public void addToolCallWidget(ToolCall call) {
        if (isDisposed()) return;
        lastToolCallWidget = new ToolCallWidget(messagesContainer, SWT.NONE, call);
        layoutAndScroll();
    }

    /**
     * Update the most recently added tool-call widget with its result.
     *
     * @param result the tool execution result
     */
    public void updateToolCallResult(ToolResult result) {
        if (lastToolCallWidget != null && !lastToolCallWidget.isDisposed()) {
            lastToolCallWidget.setResult(result);
        }
    }

    /**
     * Add a diff preview widget to the message area. The widget shows a
     * unified diff with Accept / Reject / Edit buttons.
     *
     * @param diffRequest the proposed change
     */
    public void addDiffPreview(DiffRequest diffRequest) {
        if (isDisposed()) return;
        new DiffPreviewWidget(messagesContainer, SWT.NONE, diffRequest);
        layoutAndScroll();
    }

    /**
     * Updates the list of available contexts (all open editors) in the
     * context selector dropdown.
     */
    public void setAvailableContexts(java.util.List<AdtContext> contexts) {
        if (contextSelector != null && !contextSelector.isDisposed()) {
            contextSelector.setAvailableContexts(contexts);
        }
    }

    /**
     * Programmatically adds a context as selected in the selector.
     */
    public void addSelectedContext(AdtContext ctx) {
        if (contextSelector != null && !contextSelector.isDisposed()) {
            contextSelector.addContext(ctx);
        }
    }

    /**
     * Returns the currently selected contexts from the selector.
     */
    public java.util.List<AdtContext> getSelectedContexts() {
        if (contextSelector != null && !contextSelector.isDisposed()) {
            return contextSelector.getSelectedContexts();
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Clears all context selections (used on New Chat).
     */
    public void clearContextSelections() {
        if (contextSelector != null && !contextSelector.isDisposed()) {
            contextSelector.clearSelections();
        }
    }

    /**
     * Remove all message widgets from the messages area.
     */
    public void clearMessages() {
        if (messagesContainer == null || messagesContainer.isDisposed()) {
            return;
        }
        for (Control child : messagesContainer.getChildren()) {
            child.dispose();
        }
        currentStreamingText = null;
        lastToolCallWidget = null;
        layoutAndScroll();
    }

    /**
     * Returns the input StyledText widget. Used by
     * {@link AiAssistantView#setFocus()} to give focus to the input.
     */
    public StyledText getInputText() {
        return inputText;
    }

    /**
     * Scroll the messages area to the bottom.
     */
    public void scrollToBottom() {
        if (scrolledComposite == null || scrolledComposite.isDisposed()) {
            return;
        }
        getDisplay().asyncExec(() -> {
            if (scrolledComposite.isDisposed() || messagesContainer.isDisposed()) {
                return;
            }
            scrolledComposite.setMinSize(messagesContainer.computeSize(
                    scrolledComposite.getClientArea().width, SWT.DEFAULT));
            messagesContainer.layout(true, true);
            scrolledComposite.layout(true, true);

            int maxScroll = messagesContainer.getSize().y - scrolledComposite.getClientArea().height;
            if (maxScroll > 0) {
                scrolledComposite.setOrigin(0, maxScroll);
            }
        });
    }

    /**
     * Enable or disable the send / stop buttons.
     *
     * @param sending {@code true} while the agent is running
     */
    public void setRunning(boolean sending) {
        if (isDisposed()) return;
        sendButton.setEnabled(!sending);
        stopButton.setEnabled(sending);
        inputText.setEnabled(!sending);
    }

    // ==================================================================
    // Widget creation
    // ==================================================================

    private void createMessagesArea() {
        scrolledComposite = new ScrolledComposite(this, SWT.V_SCROLL | SWT.BORDER);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);

        messagesContainer = new Composite(scrolledComposite, SWT.NONE);
        GridLayout ml = new GridLayout(1, false);
        ml.marginWidth = 4;
        ml.marginHeight = 4;
        ml.verticalSpacing = 6;
        messagesContainer.setLayout(ml);

        // Set explicit background so chat area looks consistent on any theme
        Color sysBg = getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
        scrolledComposite.setBackground(sysBg);
        messagesContainer.setBackground(sysBg);

        scrolledComposite.setContent(messagesContainer);
        scrolledComposite.setMinSize(messagesContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        // Re-compute scrolled size on resize
        scrolledComposite.addListener(SWT.Resize, e -> {
            int width = scrolledComposite.getClientArea().width;
            scrolledComposite.setMinSize(messagesContainer.computeSize(width, SWT.DEFAULT));
        });
    }

    private void createContextBar() {
        contextSelector = new ContextSelectorComposite(this, SWT.NONE);
        GridData cgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        contextSelector.setLayoutData(cgd);
    }

    private void createInputArea() {
        // Separator above input area
        Label inputSep = new Label(this, SWT.SEPARATOR | SWT.HORIZONTAL);
        inputSep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Composite inputArea = new Composite(this, SWT.NONE);
        GridLayout il = new GridLayout(1, false);
        il.marginWidth = 8;
        il.marginHeight = 6;
        inputArea.setLayout(il);
        inputArea.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));

        // Multi-line input
        inputText = new StyledText(inputArea, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        GridData inputGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputGd.heightHint = 60;
        inputText.setLayoutData(inputGd);

        // Mention popup key handling (must be registered before send listener)
        inputText.addListener(SWT.KeyDown, event -> {
            if (mentionPopup != null && mentionPopup.isVisible()) {
                if (event.keyCode == SWT.ARROW_DOWN) {
                    mentionPopup.moveSelection(1);
                    event.doit = false;
                    return;
                } else if (event.keyCode == SWT.ARROW_UP) {
                    mentionPopup.moveSelection(-1);
                    event.doit = false;
                    return;
                } else if (event.keyCode == SWT.CR || event.keyCode == SWT.TAB) {
                    mentionPopup.acceptSelection();
                    event.doit = false;
                    return;
                } else if (event.keyCode == SWT.ESC) {
                    mentionPopup.dispose();
                    event.doit = false;
                    return;
                }
            }
        });

        // Enter sends, Shift+Enter inserts newline
        inputText.addListener(SWT.KeyDown, event -> {
            if (event.keyCode == SWT.CR || event.keyCode == SWT.KEYPAD_CR) {
                if ((event.stateMask & SWT.SHIFT) == 0) {
                    // Don't send if mention popup just consumed this Enter
                    if (mentionPopup != null && mentionPopup.isVisible()) return;
                    event.doit = false;
                    doSend();
                }
            }
        });

        // Detect @ mentions while typing
        inputText.addListener(SWT.Modify, event -> handleMentionDetection());

        // Buttons row
        Composite buttonsRow = new Composite(inputArea, SWT.NONE);
        GridLayout bl = new GridLayout(3, false);
        bl.marginWidth = 0;
        bl.marginHeight = 0;
        bl.horizontalSpacing = 6;
        buttonsRow.setLayout(bl);
        buttonsRow.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        // New Chat button
        newChatButton = new Button(buttonsRow, SWT.PUSH);
        newChatButton.setText("New Chat");
        newChatButton.addListener(SWT.Selection, e -> {
            if (newChatHandler != null) {
                newChatHandler.run();
            }
        });

        // Stop button
        stopButton = new Button(buttonsRow, SWT.PUSH);
        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        stopButton.addListener(SWT.Selection, e -> {
            if (stopHandler != null) {
                stopHandler.run();
            }
        });

        // Send button (primary action, last on right)
        sendButton = new Button(buttonsRow, SWT.PUSH);
        sendButton.setText("  Send  ");
        sendButton.addListener(SWT.Selection, e -> doSend());
    }

    // ==================================================================
    // Internal helpers
    // ==================================================================

    private void doSend() {
        if (inputText == null || inputText.isDisposed()) return;
        String text = inputText.getText().trim();
        if (text.isEmpty()) return;

        // Dismiss mention popup if open
        if (mentionPopup != null) mentionPopup.dispose();

        // Resolve @ mentions before sending
        text = resolveMentions(text);

        inputText.setText("");
        if (sendHandler != null) {
            sendHandler.accept(text);
        }
    }

    // ==================================================================
    // @ Mention support
    // ==================================================================

    private void handleMentionDetection() {
        if (inputText == null || inputText.isDisposed()) return;
        String text = inputText.getText();
        int caret = inputText.getCaretOffset();

        // Find the last @ before caret
        int atPos = text.lastIndexOf('@', caret - 1);
        if (atPos < 0) {
            if (mentionPopup != null) mentionPopup.dispose();
            return;
        }

        // Check there is no space/newline between @ and caret
        String afterAt = text.substring(atPos + 1, caret);
        if (afterAt.contains(" ") || afterAt.contains("\n")) {
            if (mentionPopup != null) mentionPopup.dispose();
            return;
        }

        // Show/update popup
        if (mentionPopup == null) {
            mentionPopup = new MentionPopup(inputText, this::insertMention);
        }
        mentionPopup.show(afterAt);
    }

    private void insertMention(String mention) {
        if (inputText == null || inputText.isDisposed()) return;
        String text = inputText.getText();
        int caret = inputText.getCaretOffset();

        // Find the @ that started this mention
        int atPos = text.lastIndexOf('@', caret - 1);
        if (atPos < 0) return;

        // Replace from @ to caret with the mention
        String before = text.substring(0, atPos);
        String after = text.substring(caret);
        String newText = before + mention + " " + after;
        inputText.setText(newText);
        int newCaret = atPos + mention.length() + 1;
        inputText.setCaretOffset(newCaret);

        // Apply bold+blue styling to the mention
        StyleRange style = new StyleRange();
        style.start = atPos;
        style.length = mention.length();
        style.fontStyle = SWT.BOLD;
        style.foreground = getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE);
        inputText.setStyleRange(style);
    }

    /**
     * Resolves @ mentions by replacing them with actual content from the
     * active editor context.
     */
    private String resolveMentions(String text) {
        if (!text.contains("@")) return text;

        AdtContext context = getCurrentEditorContext();

        // @errors → inject current errors
        if (text.contains("@errors")) {
            String replacement;
            if (context != null && context.getErrors() != null
                    && !context.getErrors().isEmpty()) {
                StringBuilder sb = new StringBuilder("\n[Current errors:\n");
                for (String e : context.getErrors()) {
                    sb.append("- ").append(e).append("\n");
                }
                sb.append("]");
                replacement = sb.toString();
            } else {
                replacement = "\n[No errors found in current editor]";
            }
            text = text.replace("@errors", replacement);
        }

        // @selection → inject selected text
        if (text.contains("@selection")) {
            String replacement;
            if (context != null && context.getSelectedText() != null
                    && !context.getSelectedText().isEmpty()) {
                replacement = "\n[Selected code:\n```abap\n"
                        + context.getSelectedText() + "\n```\n]";
            } else {
                replacement = "\n[No text selected in editor]";
            }
            text = text.replace("@selection", replacement);
        }

        // @source → inject full source
        if (text.contains("@source")) {
            String replacement;
            if (context != null && context.getSourceCode() != null
                    && !context.getSourceCode().isEmpty()) {
                replacement = "\n[Full source of "
                        + (context.getObjectName() != null ? context.getObjectName() : "object")
                        + ":\n```abap\n" + context.getSourceCode() + "\n```\n]";
            } else {
                replacement = "\n[No source code available]";
            }
            text = text.replace("@source", replacement);
        }

        // @OBJECT_NAME → hint for the agent to fetch it
        Pattern p = Pattern.compile("@([A-Za-z_][A-Za-z0-9_]*)");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            // Skip already-resolved standard mentions
            if (!name.equals("errors") && !name.equals("selection")
                    && !name.equals("source")) {
                m.appendReplacement(sb,
                        "[Please use sap_get_source to read the source of object \""
                        + name.toUpperCase() + "\"]");
            }
        }
        m.appendTail(sb);
        text = sb.toString();

        return text;
    }

    private AdtContext getCurrentEditorContext() {
        try {
            org.eclipse.ui.IWorkbenchPage page = org.eclipse.ui.PlatformUI
                    .getWorkbench().getActiveWorkbenchWindow().getActivePage();
            if (page != null && page.getActiveEditor() != null) {
                return AdtEditorHelper.extractContext(page.getActiveEditor());
            }
        } catch (Exception e) {
            // Workbench may not be available
        }
        return null;
    }

    private void layoutAndScroll() {
        if (messagesContainer != null && !messagesContainer.isDisposed()) {
            messagesContainer.layout(true, true);
        }
        scrollToBottom();
    }

    @Override
    public void dispose() {
        if (messageRenderer != null) {
            messageRenderer.dispose();
            messageRenderer = null;
        }
        super.dispose();
    }
}
