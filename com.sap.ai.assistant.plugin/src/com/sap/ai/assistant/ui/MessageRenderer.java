package com.sap.ai.assistant.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * Creates styled message widgets for the chat view.
 * <p>
 * Each factory method builds a small Composite containing an icon label
 * and the message body. Three message types are supported:
 * user, assistant (streaming), and tool result.
 * </p>
 */
public class MessageRenderer {

    // Colour constants (RGB)
    private static final int[] USER_BG    = { 220, 235, 252 };
    private static final int[] ASSIST_BG  = { 245, 245, 245 };
    private static final int[] TOOL_BG    = { 238, 238, 238 };

    // Unicode icons
    private static final String USER_ICON  = "\uD83D\uDC64"; // bust in silhouette
    private static final String BOT_ICON   = "\uD83E\uDD16"; // robot face
    private static final String TOOL_ICON  = "\uD83D\uDD27"; // wrench

    private final Display display;
    private Font codeFont;

    /**
     * Create a new renderer.
     *
     * @param parent  parent composite (used only for font resolution)
     * @param display the SWT display
     */
    public MessageRenderer(Composite parent, Display display) {
        this.display = display;
        createFonts();
    }

    // ------------------------------------------------------------------
    // Public API -- message creation
    // ------------------------------------------------------------------

    /**
     * Create a user message bubble.
     *
     * @param parent the parent composite to contain the message
     * @param text   the user's message text
     * @return the created Composite
     */
    public Composite createUserMessage(Composite parent, String text) {
        Composite bubble = createBubble(parent, USER_BG);

        Label icon = new Label(bubble, SWT.NONE);
        icon.setText(USER_ICON);
        icon.setBackground(bubble.getBackground());
        GridData iconGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        icon.setLayoutData(iconGd);

        StyledText body = createMessageText(bubble, text);
        body.setBackground(bubble.getBackground());

        return bubble;
    }

    /**
     * Create an assistant message widget ready for streaming tokens.
     * The returned {@link StyledText} starts empty; callers append tokens
     * to it as they arrive.
     *
     * @param parent the parent composite
     * @return the StyledText to which streaming tokens should be appended
     */
    public StyledText createAssistantMessage(Composite parent) {
        Composite bubble = createBubble(parent, ASSIST_BG);

        Label icon = new Label(bubble, SWT.NONE);
        icon.setText(BOT_ICON);
        icon.setBackground(bubble.getBackground());
        GridData iconGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        icon.setLayoutData(iconGd);

        StyledText body = new StyledText(bubble, SWT.WRAP | SWT.READ_ONLY | SWT.MULTI);
        body.setBackground(bubble.getBackground());
        body.setEditable(false);
        body.setWordWrap(true);
        body.setCaret(null);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        body.setLayoutData(gd);

        return body;
    }

    /**
     * Create a small tool result message.
     *
     * @param parent the parent composite
     * @param text   the tool result summary text
     * @return the created Composite
     */
    public Composite createToolMessage(Composite parent, String text) {
        Composite bubble = createBubble(parent, TOOL_BG);

        Label icon = new Label(bubble, SWT.NONE);
        icon.setText(TOOL_ICON);
        icon.setBackground(bubble.getBackground());
        GridData iconGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        icon.setLayoutData(iconGd);

        StyledText body = createMessageText(bubble, text);
        body.setBackground(bubble.getBackground());
        body.setFont(codeFont);

        return bubble;
    }

    /**
     * Dispose fonts and colour resources.
     */
    public void dispose() {
        if (codeFont != null && !codeFont.isDisposed()) {
            codeFont.dispose();
            codeFont = null;
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void createFonts() {
        // Try Menlo (macOS), then Consolas (Windows), then Courier
        String[] candidates = { "Menlo", "Consolas", "Courier New", "Courier" };
        for (String name : candidates) {
            try {
                codeFont = new Font(display, name, 11, SWT.NORMAL);
                if (codeFont.getFontData().length > 0) {
                    return;
                }
            } catch (Exception e) {
                // Try next
            }
        }
    }

    /**
     * Create a rounded-corner-like bubble Composite with a two-column
     * GridLayout (icon | text).
     */
    private Composite createBubble(Composite parent, int[] rgb) {
        Composite bubble = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 10;
        layout.marginHeight = 8;
        layout.horizontalSpacing = 8;
        bubble.setLayout(layout);

        Color bg = new Color(display, rgb[0], rgb[1], rgb[2]);
        bubble.setBackground(bg);

        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        bubble.setLayoutData(gd);

        return bubble;
    }

    /**
     * Create a read-only, word-wrapping StyledText with the given text.
     */
    private StyledText createMessageText(Composite parent, String text) {
        StyledText st = new StyledText(parent, SWT.WRAP | SWT.READ_ONLY | SWT.MULTI);
        st.setText(text != null ? text : "");
        st.setEditable(false);
        st.setWordWrap(true);
        st.setCaret(null);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        st.setLayoutData(gd);
        return st;
    }
}
