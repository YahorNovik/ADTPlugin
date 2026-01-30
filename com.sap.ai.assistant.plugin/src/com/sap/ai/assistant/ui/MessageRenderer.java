package com.sap.ai.assistant.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * Creates styled message widgets for the chat view.
 */
public class MessageRenderer {

    // Colour constants (RGB) -- softer, modern palette
    private static final int[] USER_BG    = { 0, 122, 204 };   // Blue accent
    private static final int[] USER_FG    = { 255, 255, 255 };  // White text
    private static final int[] ASSIST_BG  = { 250, 250, 250 };  // Near-white
    private static final int[] ASSIST_FG  = { 30, 30, 30 };     // Dark text
    private static final int[] ERROR_BG   = { 255, 235, 235 };  // Light red
    private static final int[] ERROR_FG   = { 180, 0, 0 };      // Red text
    private static final int[] TOOL_BG    = { 240, 240, 240 };

    // Labels
    private static final String USER_LABEL = "You";
    private static final String BOT_LABEL  = "AI Assistant";

    private final Display display;
    private Font codeFont;

    public MessageRenderer(Composite parent, Display display) {
        this.display = display;
        createFonts();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public Composite createUserMessage(Composite parent, String text) {
        // Check if it's an error message
        if (text != null && text.startsWith("Error:")) {
            return createErrorMessage(parent, text);
        }

        Composite bubble = createBubble(parent, USER_BG);

        Label roleLabel = new Label(bubble, SWT.NONE);
        roleLabel.setText(USER_LABEL);
        roleLabel.setForeground(new Color(display, USER_FG[0], USER_FG[1], USER_FG[2]));
        roleLabel.setBackground(bubble.getBackground());
        roleLabel.setFont(getBoldFont(roleLabel.getFont()));
        GridData rlGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        roleLabel.setLayoutData(rlGd);

        StyledText body = createMessageText(bubble, text);
        body.setBackground(bubble.getBackground());
        body.setForeground(new Color(display, USER_FG[0], USER_FG[1], USER_FG[2]));

        return bubble;
    }

    public Composite createErrorMessage(Composite parent, String text) {
        Composite bubble = createBubble(parent, ERROR_BG);

        Label roleLabel = new Label(bubble, SWT.NONE);
        roleLabel.setText("Error");
        roleLabel.setForeground(new Color(display, ERROR_FG[0], ERROR_FG[1], ERROR_FG[2]));
        roleLabel.setBackground(bubble.getBackground());
        roleLabel.setFont(getBoldFont(roleLabel.getFont()));
        GridData rlGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        roleLabel.setLayoutData(rlGd);

        String errorText = text;
        if (errorText != null && errorText.startsWith("Error: ")) {
            errorText = errorText.substring(7);
        }

        StyledText body = createMessageText(bubble, errorText);
        body.setBackground(bubble.getBackground());
        body.setForeground(new Color(display, ERROR_FG[0], ERROR_FG[1], ERROR_FG[2]));

        return bubble;
    }

    public StyledText createAssistantMessage(Composite parent) {
        Composite bubble = createBubble(parent, ASSIST_BG);

        Label roleLabel = new Label(bubble, SWT.NONE);
        roleLabel.setText(BOT_LABEL);
        roleLabel.setForeground(new Color(display, 0, 122, 204));
        roleLabel.setBackground(bubble.getBackground());
        roleLabel.setFont(getBoldFont(roleLabel.getFont()));
        GridData rlGd = new GridData(SWT.BEGINNING, SWT.TOP, true, false, 2, 1);
        roleLabel.setLayoutData(rlGd);

        // Separator line
        Label sep = new Label(bubble, SWT.SEPARATOR | SWT.HORIZONTAL);
        GridData sepGd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        sep.setLayoutData(sepGd);

        StyledText body = new StyledText(bubble, SWT.WRAP | SWT.READ_ONLY | SWT.MULTI);
        body.setBackground(bubble.getBackground());
        body.setForeground(new Color(display, ASSIST_FG[0], ASSIST_FG[1], ASSIST_FG[2]));
        body.setEditable(false);
        body.setWordWrap(true);
        body.setCaret(null);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1);
        body.setLayoutData(gd);

        return body;
    }

    public Composite createToolMessage(Composite parent, String text) {
        Composite bubble = createBubble(parent, TOOL_BG);

        Label icon = new Label(bubble, SWT.NONE);
        icon.setText("Tool");
        icon.setBackground(bubble.getBackground());
        icon.setForeground(new Color(display, 100, 100, 100));
        GridData iconGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        icon.setLayoutData(iconGd);

        StyledText body = createMessageText(bubble, text);
        body.setBackground(bubble.getBackground());
        body.setFont(codeFont);

        return bubble;
    }

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

    private Font getBoldFont(Font base) {
        try {
            FontData[] fd = base.getFontData();
            if (fd.length > 0) {
                return new Font(display, fd[0].getName(), fd[0].getHeight(), SWT.BOLD);
            }
        } catch (Exception e) {
            // ignore
        }
        return base;
    }

    private Composite createBubble(Composite parent, int[] rgb) {
        Composite bubble = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 12;
        layout.marginHeight = 10;
        layout.horizontalSpacing = 8;
        layout.verticalSpacing = 4;
        bubble.setLayout(layout);

        Color bg = new Color(display, rgb[0], rgb[1], rgb[2]);
        bubble.setBackground(bg);

        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        bubble.setLayoutData(gd);

        return bubble;
    }

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
