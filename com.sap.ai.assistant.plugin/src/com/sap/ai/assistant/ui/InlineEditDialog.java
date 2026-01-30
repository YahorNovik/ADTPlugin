package com.sap.ai.assistant.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A lightweight modal dialog that asks the user to describe a code change.
 * Used by the Inline Edit command (Ctrl+Shift+K / Cmd+Shift+K).
 * <p>
 * Shows a preview of the selected code and a single-line text input.
 * Enter submits, Escape cancels.
 * </p>
 */
public class InlineEditDialog extends Dialog {

    private final String objectName;
    private final String selectedText;
    private String result;

    public InlineEditDialog(Shell parent, String objectName, String selectedText) {
        super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        this.objectName = objectName;
        this.selectedText = selectedText;
    }

    /**
     * Opens the dialog and blocks until the user submits or cancels.
     *
     * @return the user's instruction text, or {@code null} if cancelled
     */
    public String open() {
        Shell shell = new Shell(getParent(), getStyle());
        shell.setText("AI Inline Edit"
                + (objectName != null ? " \u2014 " + objectName : ""));
        shell.setLayout(new GridLayout(1, false));

        // Context label showing selected code preview
        if (selectedText != null && !selectedText.isEmpty()) {
            Label contextLabel = new Label(shell, SWT.WRAP);
            String preview = selectedText.length() > 120
                    ? selectedText.substring(0, 120) + "\u2026"
                    : selectedText;
            contextLabel.setText("Selected: " + preview.replace("\n", " "));
            contextLabel.setForeground(
                    shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
            GridData ctxGd = new GridData(SWT.FILL, SWT.TOP, true, false);
            ctxGd.widthHint = 460;
            contextLabel.setLayoutData(ctxGd);
        }

        // Instruction label
        Label label = new Label(shell, SWT.NONE);
        label.setText("What change do you want to make?");

        // Text input
        Text input = new Text(shell, SWT.SINGLE | SWT.BORDER);
        input.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        input.setFocus();

        // Enter submits, Escape cancels
        input.addListener(SWT.KeyDown, e -> {
            if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
                result = input.getText().trim();
                shell.close();
            } else if (e.keyCode == SWT.ESC) {
                result = null;
                shell.close();
            }
        });

        shell.setSize(500, selectedText != null ? 160 : 120);

        // Centre on parent
        shell.setLocation(
                getParent().getBounds().x
                        + (getParent().getBounds().width - shell.getSize().x) / 2,
                getParent().getBounds().y
                        + (getParent().getBounds().height - shell.getSize().y) / 2);

        shell.open();
        Display display = getParent().getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return result;
    }
}
