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

import com.sap.ai.assistant.model.ToolCall;
import com.sap.ai.assistant.model.ToolResult;

/**
 * A collapsible widget that displays a tool call invocation and its result.
 * <p>
 * In its collapsed state only a one-line header is visible
 * (e.g. "&#x25B6; Tool: sap_search_object"). Clicking the header toggles
 * between collapsed and expanded states. When expanded the arguments JSON
 * and, once available, the tool result are shown in a monospace text area.
 * </p>
 */
public class ToolCallWidget extends Composite {

    private static final String ARROW_COLLAPSED = "\u25B6"; // ▶
    private static final String ARROW_EXPANDED  = "\u25BC"; // ▼

    private final ToolCall toolCall;
    private boolean expanded;

    // Child widgets
    private Label headerLabel;
    private Composite detailsComposite;
    private StyledText argumentsText;
    private StyledText resultText;
    private Label resultStatusLabel;
    private Font codeFont;

    /**
     * Create a new tool-call widget.
     *
     * @param parent   the parent composite
     * @param style    SWT style bits
     * @param toolCall the tool call to display
     */
    public ToolCallWidget(Composite parent, int style, ToolCall toolCall) {
        super(parent, style);
        this.toolCall = toolCall;
        this.expanded = false;

        Display display = getDisplay();
        createCodeFont(display);

        Color bg = new Color(display, 248, 248, 248);
        setBackground(bg);

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 8;
        layout.marginHeight = 4;
        setLayout(layout);

        GridData outerGd = new GridData(SWT.FILL, SWT.TOP, true, false);
        setLayoutData(outerGd);

        createHeader();
        createDetails();
    }

    /**
     * Populate the result section once the tool execution completes.
     *
     * @param result the tool execution result
     */
    public void setResult(ToolResult result) {
        if (result == null || isDisposed()) {
            return;
        }
        getDisplay().asyncExec(() -> {
            if (isDisposed()) return;
            if (resultText != null && !resultText.isDisposed()) {
                String content = result.getContent();
                resultText.setText(content != null ? content : "(no output)");
            }
            if (resultStatusLabel != null && !resultStatusLabel.isDisposed()) {
                if (result.isError()) {
                    resultStatusLabel.setText("  [ERROR]");
                    resultStatusLabel.setForeground(new Color(getDisplay(), 200, 0, 0));
                } else {
                    resultStatusLabel.setText("  [OK]");
                    resultStatusLabel.setForeground(new Color(getDisplay(), 0, 140, 0));
                }
            }
            // Update header to reflect completion
            if (headerLabel != null && !headerLabel.isDisposed()) {
                String arrow = expanded ? ARROW_EXPANDED : ARROW_COLLAPSED;
                String status = result.isError() ? " [ERROR]" : " [done]";
                headerLabel.setText(arrow + " Tool: " + toolCall.getName() + status);
            }
            requestLayout();
        });
    }

    // ------------------------------------------------------------------
    // Widget creation
    // ------------------------------------------------------------------

    private void createHeader() {
        Composite headerComposite = new Composite(this, SWT.NONE);
        headerComposite.setBackground(getBackground());
        GridLayout hl = new GridLayout(2, false);
        hl.marginWidth = 0;
        hl.marginHeight = 0;
        headerComposite.setLayout(hl);
        headerComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        headerLabel = new Label(headerComposite, SWT.NONE);
        headerLabel.setText(ARROW_COLLAPSED + " Tool: " + toolCall.getName());
        headerLabel.setBackground(getBackground());
        headerLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        headerLabel.setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));

        resultStatusLabel = new Label(headerComposite, SWT.NONE);
        resultStatusLabel.setText("  [running...]");
        resultStatusLabel.setForeground(new Color(getDisplay(), 150, 150, 150));
        resultStatusLabel.setBackground(getBackground());

        // Toggle on click
        headerLabel.addListener(SWT.MouseUp, e -> toggleExpanded());
        headerComposite.addListener(SWT.MouseUp, e -> toggleExpanded());
    }

    private void createDetails() {
        detailsComposite = new Composite(this, SWT.NONE);
        detailsComposite.setBackground(getBackground());
        GridLayout dl = new GridLayout(1, false);
        dl.marginWidth = 16;
        dl.marginHeight = 4;
        detailsComposite.setLayout(dl);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        detailsComposite.setLayoutData(gd);

        // Arguments section
        Label argsLabel = new Label(detailsComposite, SWT.NONE);
        argsLabel.setText("Arguments:");
        argsLabel.setBackground(getBackground());

        argumentsText = new StyledText(detailsComposite, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.BORDER);
        argumentsText.setEditable(false);
        argumentsText.setWordWrap(true);
        argumentsText.setCaret(null);
        if (codeFont != null) {
            argumentsText.setFont(codeFont);
        }
        String argsJson = toolCall.getArguments() != null
                ? toolCall.getArguments().toString() : "{}";
        argumentsText.setText(formatJson(argsJson));
        GridData argsGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        argsGd.heightHint = 60;
        argumentsText.setLayoutData(argsGd);

        // Result section
        Label resLabel = new Label(detailsComposite, SWT.NONE);
        resLabel.setText("Result:");
        resLabel.setBackground(getBackground());

        resultText = new StyledText(detailsComposite, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.BORDER);
        resultText.setEditable(false);
        resultText.setWordWrap(true);
        resultText.setCaret(null);
        if (codeFont != null) {
            resultText.setFont(codeFont);
        }
        resultText.setText("(waiting for result...)");
        GridData resGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        resGd.heightHint = 60;
        resultText.setLayoutData(resGd);

        // Start collapsed
        detailsComposite.setVisible(false);
        ((GridData) detailsComposite.getLayoutData()).exclude = true;
    }

    // ------------------------------------------------------------------
    // Toggle
    // ------------------------------------------------------------------

    private void toggleExpanded() {
        if (isDisposed()) return;
        expanded = !expanded;

        String arrow = expanded ? ARROW_EXPANDED : ARROW_COLLAPSED;
        String currentText = headerLabel.getText();
        // Preserve any status suffix after the tool name
        int colonIdx = currentText.indexOf("Tool:");
        String suffix = "";
        if (colonIdx >= 0) {
            suffix = currentText.substring(colonIdx);
        } else {
            suffix = "Tool: " + toolCall.getName();
        }
        headerLabel.setText(arrow + " " + suffix);

        detailsComposite.setVisible(expanded);
        GridData gd = (GridData) detailsComposite.getLayoutData();
        gd.exclude = !expanded;

        requestLayout();
        // Trigger parent layout up the chain
        Composite parent = getParent();
        while (parent != null) {
            parent.layout(true, true);
            parent = parent.getParent();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void createCodeFont(Display display) {
        String[] candidates = { "Menlo", "Consolas", "Courier New", "Courier" };
        for (String name : candidates) {
            try {
                codeFont = new Font(display, name, 10, SWT.NORMAL);
                if (codeFont.getFontData().length > 0) {
                    return;
                }
            } catch (Exception e) {
                // Try next
            }
        }
    }

    /**
     * Simple indentation pass for compact JSON -- not a full formatter.
     */
    private static String formatJson(String json) {
        if (json == null) return "";
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (!inString) {
                switch (c) {
                    case '{': case '[':
                        sb.append(c).append('\n');
                        indent++;
                        appendIndent(sb, indent);
                        break;
                    case '}': case ']':
                        sb.append('\n');
                        indent--;
                        appendIndent(sb, indent);
                        sb.append(c);
                        break;
                    case ',':
                        sb.append(c).append('\n');
                        appendIndent(sb, indent);
                        break;
                    case ':':
                        sb.append(": ");
                        break;
                    default:
                        if (!Character.isWhitespace(c)) {
                            sb.append(c);
                        }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void appendIndent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    @Override
    public void dispose() {
        if (codeFont != null && !codeFont.isDisposed()) {
            codeFont.dispose();
        }
        super.dispose();
    }
}
