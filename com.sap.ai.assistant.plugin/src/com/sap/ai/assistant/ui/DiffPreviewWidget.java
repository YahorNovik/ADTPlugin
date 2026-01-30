package com.sap.ai.assistant.ui;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.sap.ai.assistant.model.DiffRequest;

/**
 * SWT widget that shows a unified diff with Accept / Reject / Edit buttons.
 * Inserted into the chat message area when a write tool is intercepted.
 */
public class DiffPreviewWidget extends Composite {

    private static final int MAX_DISPLAY_LINES = 500;

    private final DiffRequest diffRequest;
    private StyledText diffText;
    private Button acceptButton;
    private Button rejectButton;
    private Button editButton;
    private Label statusLabel;
    private Font codeFont;
    private Font boldFont;

    public DiffPreviewWidget(Composite parent, int style, DiffRequest diffRequest) {
        super(parent, style);
        this.diffRequest = diffRequest;
        createContents();
    }

    private void createContents() {
        Display d = getDisplay();
        Color bg = new Color(d, 255, 253, 231); // light yellow
        setBackground(bg);

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 10;
        layout.marginHeight = 8;
        setLayout(layout);
        setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        createHeader();
        createDiffDisplay();
        createButtonBar();
    }

    private void createHeader() {
        Label header = new Label(this, SWT.NONE);
        header.setText("Proposed Change: " + diffRequest.getObjectName()
                + "  [" + diffRequest.getToolName() + "]");
        header.setBackground(getBackground());
        header.setFont(getBoldFont());
        header.setForeground(new Color(getDisplay(), 80, 80, 0));
        header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createDiffDisplay() {
        diffText = new StyledText(this, SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        diffText.setEditable(false);
        diffText.setCaret(null);
        diffText.setFont(getCodeFont());

        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        gd.heightHint = 300;
        diffText.setLayoutData(gd);

        // Compute and display unified diff
        List<DiffComputer.DiffLine> diffLines = DiffComputer.computeDiff(
                diffRequest.getOldSource(), diffRequest.getNewSource(), 3);

        String formatted = DiffComputer.formatUnifiedDiff(diffLines, diffRequest.getObjectName());

        // Truncate if too large
        String[] lines = formatted.split("\n", -1);
        if (lines.length > MAX_DISPLAY_LINES) {
            StringBuilder truncated = new StringBuilder();
            for (int i = 0; i < MAX_DISPLAY_LINES; i++) {
                truncated.append(lines[i]).append('\n');
            }
            truncated.append("... (showing first ").append(MAX_DISPLAY_LINES).append(" lines of diff)");
            formatted = truncated.toString();
        }

        diffText.setText(formatted);
        applyDiffStyling(diffText, formatted);
    }

    private void createButtonBar() {
        Composite bar = new Composite(this, SWT.NONE);
        bar.setBackground(getBackground());
        GridLayout bl = new GridLayout(4, false);
        bl.marginWidth = 0;
        bl.horizontalSpacing = 8;
        bar.setLayout(bl);
        bar.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        acceptButton = new Button(bar, SWT.PUSH);
        acceptButton.setText("  Accept  ");
        acceptButton.addListener(SWT.Selection, e -> handleAccept());

        editButton = new Button(bar, SWT.PUSH);
        editButton.setText("Edit");
        editButton.addListener(SWT.Selection, e -> handleEdit());

        rejectButton = new Button(bar, SWT.PUSH);
        rejectButton.setText("Reject");
        rejectButton.addListener(SWT.Selection, e -> handleReject());

        statusLabel = new Label(bar, SWT.NONE);
        statusLabel.setText("");
        statusLabel.setBackground(getBackground());
    }

    private void handleAccept() {
        diffRequest.setDecision(DiffRequest.Decision.ACCEPTED);
        disableButtons();
        statusLabel.setText("Accepted - applying...");
        statusLabel.setForeground(new Color(getDisplay(), 0, 140, 0));
        statusLabel.requestLayout();
    }

    private void handleReject() {
        diffRequest.setDecision(DiffRequest.Decision.REJECTED);
        disableButtons();
        statusLabel.setText("Rejected");
        statusLabel.setForeground(new Color(getDisplay(), 200, 0, 0));
        statusLabel.requestLayout();
    }

    private void handleEdit() {
        EditSourceDialog dialog = new EditSourceDialog(
                getShell(), diffRequest.getNewSource(), diffRequest.getObjectName());
        String edited = dialog.open();
        if (edited != null) {
            diffRequest.setDecision(DiffRequest.Decision.EDITED, edited);
            disableButtons();
            statusLabel.setText("Edited - applying...");
            statusLabel.setForeground(new Color(getDisplay(), 0, 100, 180));
            statusLabel.requestLayout();
        }
    }

    private void disableButtons() {
        acceptButton.setEnabled(false);
        rejectButton.setEnabled(false);
        editButton.setEnabled(false);
    }

    private void applyDiffStyling(StyledText widget, String text) {
        Display d = getDisplay();
        Color addedBg = new Color(d, 220, 255, 220);
        Color removedBg = new Color(d, 255, 220, 220);
        Color hunkFg = new Color(d, 0, 120, 200);

        String[] lines = text.split("\n", -1);
        int offset = 0;
        for (String line : lines) {
            if (line.startsWith("+ ")) {
                StyleRange range = new StyleRange();
                range.start = offset;
                range.length = line.length();
                range.background = addedBg;
                widget.setStyleRange(range);
            } else if (line.startsWith("- ")) {
                StyleRange range = new StyleRange();
                range.start = offset;
                range.length = line.length();
                range.background = removedBg;
                widget.setStyleRange(range);
            } else if (line.startsWith("---") || line.startsWith("+++")) {
                StyleRange range = new StyleRange();
                range.start = offset;
                range.length = line.length();
                range.foreground = hunkFg;
                range.fontStyle = SWT.BOLD;
                widget.setStyleRange(range);
            }
            offset += line.length() + 1; // +1 for newline
        }
    }

    private Font getCodeFont() {
        if (codeFont != null) return codeFont;
        String[] candidates = { "Menlo", "Consolas", "Courier New", "Courier" };
        for (String name : candidates) {
            try {
                codeFont = new Font(getDisplay(), name, 11, SWT.NORMAL);
                if (codeFont.getFontData().length > 0) return codeFont;
            } catch (Exception e) {
                // Try next
            }
        }
        return getFont();
    }

    private Font getBoldFont() {
        if (boldFont != null) return boldFont;
        try {
            FontData[] fd = getFont().getFontData();
            if (fd.length > 0) {
                boldFont = new Font(getDisplay(), fd[0].getName(), fd[0].getHeight(), SWT.BOLD);
                return boldFont;
            }
        } catch (Exception e) {
            // ignore
        }
        return getFont();
    }

    @Override
    public void dispose() {
        if (codeFont != null && !codeFont.isDisposed()) {
            codeFont.dispose();
        }
        if (boldFont != null && !boldFont.isDisposed()) {
            boldFont.dispose();
        }
        super.dispose();
    }
}
