package com.sap.ai.assistant.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * Creates styled message widgets for the chat view.
 * Uses only Eclipse system colors for maximum compatibility.
 */
public class MessageRenderer {

    private static final String USER_LABEL = "You";
    private static final String BOT_LABEL  = "AI Assistant";

    private final Display display;
    private Font codeFont;

    public MessageRenderer(Composite parent, Display display) {
        this.display = display;
        createFonts();
    }

    public boolean isDark() {
        return false;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public Composite createUserMessage(Composite parent, String text) {
        if (text != null && text.startsWith("Error:")) {
            return createErrorMessage(parent, text);
        }

        Composite row = createRow(parent);

        Label roleLabel = new Label(row, SWT.NONE);
        roleLabel.setText(USER_LABEL);
        roleLabel.setFont(getBoldFont(roleLabel.getFont()));
        roleLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_BLUE));
        roleLabel.setBackground(row.getBackground());
        roleLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));

        StyledText body = createMessageText(row, text);
        body.setBackground(row.getBackground());

        return row;
    }

    public Composite createErrorMessage(Composite parent, String text) {
        Composite row = createRow(parent);

        Label roleLabel = new Label(row, SWT.NONE);
        roleLabel.setText("Error");
        roleLabel.setFont(getBoldFont(roleLabel.getFont()));
        roleLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_RED));
        roleLabel.setBackground(row.getBackground());
        roleLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));

        String errorText = text;
        if (errorText != null && errorText.startsWith("Error: ")) {
            errorText = errorText.substring(7);
        }

        StyledText body = createMessageText(row, errorText);
        body.setBackground(row.getBackground());
        body.setForeground(display.getSystemColor(SWT.COLOR_DARK_RED));

        return row;
    }

    public StyledText createAssistantMessage(Composite parent) {
        Composite row = createRow(parent);

        Label roleLabel = new Label(row, SWT.NONE);
        roleLabel.setText(BOT_LABEL);
        roleLabel.setFont(getBoldFont(roleLabel.getFont()));
        roleLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_GREEN));
        roleLabel.setBackground(row.getBackground());
        roleLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, true, false, 2, 1));

        Label sep = new Label(row, SWT.SEPARATOR | SWT.HORIZONTAL);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        StyledText body = new StyledText(row, SWT.WRAP | SWT.READ_ONLY | SWT.MULTI);
        body.setBackground(row.getBackground());
        body.setEditable(false);
        body.setWordWrap(true);
        body.setCaret(null);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));

        return body;
    }

    public Composite createToolMessage(Composite parent, String text) {
        Composite row = createRow(parent);

        Label icon = new Label(row, SWT.NONE);
        icon.setText("Tool");
        icon.setBackground(row.getBackground());
        icon.setForeground(display.getSystemColor(SWT.COLOR_DARK_GRAY));
        icon.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));

        StyledText body = createMessageText(row, text);
        body.setBackground(row.getBackground());
        body.setFont(codeFont);

        return row;
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
                if (codeFont.getFontData().length > 0) return;
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

    private Composite createRow(Composite parent) {
        Composite row = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 10;
        layout.marginHeight = 8;
        layout.horizontalSpacing = 8;
        layout.verticalSpacing = 4;
        row.setLayout(layout);
        row.setBackground(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        return row;
    }

    private StyledText createMessageText(Composite parent, String text) {
        StyledText st = new StyledText(parent, SWT.WRAP | SWT.READ_ONLY | SWT.MULTI);
        st.setText(text != null ? text : "");
        st.setEditable(false);
        st.setWordWrap(true);
        st.setCaret(null);
        st.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        return st;
    }
}
