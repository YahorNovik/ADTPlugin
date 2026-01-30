package com.sap.ai.assistant.ui;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

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
    private Label contextLabel;
    private StyledText inputText;
    private Button sendButton;
    private Button stopButton;
    private Button newChatButton;

    // ---- State ----
    private MessageRenderer messageRenderer;
    private StyledText currentStreamingText;
    private ToolCallWidget lastToolCallWidget;

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
     * Update the context information bar.
     *
     * @param text context summary text (e.g. "ZTEST_CLASS.clas [line 42]")
     */
    public void setContextLabel(String text) {
        if (contextLabel != null && !contextLabel.isDisposed()) {
            contextLabel.setText(text != null ? text : "");
            contextLabel.requestLayout();
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

        scrolledComposite.setContent(messagesContainer);
        scrolledComposite.setMinSize(messagesContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        // Re-compute scrolled size on resize
        scrolledComposite.addListener(SWT.Resize, e -> {
            int width = scrolledComposite.getClientArea().width;
            scrolledComposite.setMinSize(messagesContainer.computeSize(width, SWT.DEFAULT));
        });
    }

    private void createContextBar() {
        contextLabel = new Label(this, SWT.NONE);
        contextLabel.setText("");
        contextLabel.setForeground(new Color(getDisplay(), 120, 120, 120));
        GridData cgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        cgd.horizontalIndent = 6;
        contextLabel.setLayoutData(cgd);
    }

    private void createInputArea() {
        Composite inputArea = new Composite(this, SWT.NONE);
        GridLayout il = new GridLayout(4, false);
        il.marginWidth = 4;
        il.marginHeight = 4;
        inputArea.setLayout(il);
        inputArea.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));

        // Multi-line input (3 rows)
        inputText = new StyledText(inputArea, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        GridData inputGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputGd.heightHint = 52; // ~3 lines
        inputText.setLayoutData(inputGd);

        // Enter sends, Shift+Enter inserts newline
        inputText.addListener(SWT.KeyDown, event -> {
            if (event.keyCode == SWT.CR || event.keyCode == SWT.KEYPAD_CR) {
                if ((event.stateMask & SWT.SHIFT) == 0) {
                    event.doit = false;
                    doSend();
                }
                // Shift+Enter: default behaviour -- newline
            }
        });

        // Send button
        sendButton = new Button(inputArea, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.addListener(SWT.Selection, e -> doSend());

        // Stop button
        stopButton = new Button(inputArea, SWT.PUSH);
        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        stopButton.addListener(SWT.Selection, e -> {
            if (stopHandler != null) {
                stopHandler.run();
            }
        });

        // New Chat button
        newChatButton = new Button(inputArea, SWT.PUSH);
        newChatButton.setText("New Chat");
        newChatButton.addListener(SWT.Selection, e -> {
            if (newChatHandler != null) {
                newChatHandler.run();
            }
        });
    }

    // ==================================================================
    // Internal helpers
    // ==================================================================

    private void doSend() {
        if (inputText == null || inputText.isDisposed()) return;
        String text = inputText.getText().trim();
        if (text.isEmpty()) return;
        inputText.setText("");
        if (sendHandler != null) {
            sendHandler.accept(text);
        }
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
