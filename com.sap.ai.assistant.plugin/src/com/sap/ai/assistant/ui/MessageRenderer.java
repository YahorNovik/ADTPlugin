package com.sap.ai.assistant.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * Creates styled message widgets for the chat view.
 * Adapts to Eclipse light and dark themes automatically.
 */
public class MessageRenderer {

    // Labels
    private static final String USER_LABEL = "You";
    private static final String BOT_LABEL  = "AI Assistant";

    private final Display display;
    private final boolean darkTheme;
    private Font codeFont;

    // Theme-dependent colours
    private final int[] userBg;
    private final int[] userFg;
    private final int[] assistBg;
    private final int[] assistFg;
    private final int[] errorBg;
    private final int[] errorFg;
    private final int[] toolBg;
    private final int[] toolFg;
    private final int[] accentFg;

    public MessageRenderer(Composite parent, Display display) {
        this.display = display;
        this.darkTheme = isDarkTheme(display);
        createFonts();

        if (darkTheme) {
            userBg   = new int[]{ 0, 100, 180 };      // Darker blue
            userFg   = new int[]{ 255, 255, 255 };
            assistBg = new int[]{ 45, 45, 48 };        // Dark surface
            assistFg = new int[]{ 220, 220, 220 };     // Light text
            errorBg  = new int[]{ 80, 30, 30 };        // Dark red
            errorFg  = new int[]{ 255, 130, 130 };     // Light red text
            toolBg   = new int[]{ 50, 50, 55 };
            toolFg   = new int[]{ 170, 170, 170 };
            accentFg = new int[]{ 80, 170, 255 };      // Light blue accent
        } else {
            userBg   = new int[]{ 0, 122, 204 };       // Blue accent
            userFg   = new int[]{ 255, 255, 255 };
            assistBg = new int[]{ 250, 250, 250 };     // Near-white
            assistFg = new int[]{ 30, 30, 30 };        // Dark text
            errorBg  = new int[]{ 255, 235, 235 };     // Light red
            errorFg  = new int[]{ 180, 0, 0 };         // Red text
            toolBg   = new int[]{ 240, 240, 240 };
            toolFg   = new int[]{ 100, 100, 100 };
            accentFg = new int[]{ 0, 122, 204 };       // Blue accent
        }
    }

    /** Returns true if Eclipse is using a dark theme. */
    public boolean isDark() {
        return darkTheme;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public Composite createUserMessage(Composite parent, String text) {
        // Check if it's an error message
        if (text != null && text.startsWith("Error:")) {
            return createErrorMessage(parent, text);
        }

        Composite bubble = createBubble(parent, userBg);

        Label roleLabel = new Label(bubble, SWT.NONE);
        roleLabel.setText(USER_LABEL);
        roleLabel.setForeground(new Color(display, userFg[0], userFg[1], userFg[2]));
        roleLabel.setBackground(bubble.getBackground());
        roleLabel.setFont(getBoldFont(roleLabel.getFont()));
        GridData rlGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        roleLabel.setLayoutData(rlGd);

        StyledText body = createMessageText(bubble, text);
        body.setBackground(bubble.getBackground());
        body.setForeground(new Color(display, userFg[0], userFg[1], userFg[2]));

        return bubble;
    }

    public Composite createErrorMessage(Composite parent, String text) {
        Composite bubble = createBubble(parent, errorBg);

        Label roleLabel = new Label(bubble, SWT.NONE);
        roleLabel.setText("Error");
        roleLabel.setForeground(new Color(display, errorFg[0], errorFg[1], errorFg[2]));
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
        body.setForeground(new Color(display, errorFg[0], errorFg[1], errorFg[2]));

        return bubble;
    }

    public StyledText createAssistantMessage(Composite parent) {
        Composite bubble = createBubble(parent, assistBg);

        Label roleLabel = new Label(bubble, SWT.NONE);
        roleLabel.setText(BOT_LABEL);
        roleLabel.setForeground(new Color(display, accentFg[0], accentFg[1], accentFg[2]));
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
        body.setForeground(new Color(display, assistFg[0], assistFg[1], assistFg[2]));
        body.setEditable(false);
        body.setWordWrap(true);
        body.setCaret(null);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1);
        body.setLayoutData(gd);

        return body;
    }

    public Composite createToolMessage(Composite parent, String text) {
        Composite bubble = createBubble(parent, toolBg);

        Label icon = new Label(bubble, SWT.NONE);
        icon.setText("Tool");
        icon.setBackground(bubble.getBackground());
        icon.setForeground(new Color(display, toolFg[0], toolFg[1], toolFg[2]));
        GridData iconGd = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
        icon.setLayoutData(iconGd);

        StyledText body = createMessageText(bubble, text);
        body.setBackground(bubble.getBackground());
        body.setForeground(new Color(display, assistFg[0], assistFg[1], assistFg[2]));
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

    private static boolean isDarkTheme(Display display) {
        Color bg = display.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
        if (bg != null) {
            RGB rgb = bg.getRGB();
            // Perceived brightness: dark if < 128
            double brightness = (rgb.red * 0.299 + rgb.green * 0.587 + rgb.blue * 0.114);
            return brightness < 128;
        }
        return false;
    }

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
