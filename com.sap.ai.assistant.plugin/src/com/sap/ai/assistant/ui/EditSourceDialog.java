package com.sap.ai.assistant.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Modal dialog for editing proposed source code before it is applied to SAP.
 */
public class EditSourceDialog extends Dialog {

    private final String source;
    private final String objectName;
    private String result;
    private Font codeFont;

    public EditSourceDialog(Shell parent, String source, String objectName) {
        super(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
        this.source = source;
        this.objectName = objectName;
    }

    /**
     * Opens the dialog and returns the edited source code, or {@code null}
     * if the user cancelled.
     */
    public String open() {
        Shell shell = new Shell(getParent(), getStyle());
        shell.setText("Edit Source: " + objectName);
        shell.setSize(900, 700);
        shell.setLayout(new GridLayout(1, false));

        // Source editor
        StyledText editor = new StyledText(shell,
                SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        editor.setText(source != null ? source : "");
        editor.setFont(getCodeFont(shell.getDisplay()));
        editor.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Button bar
        Composite buttons = new Composite(shell, SWT.NONE);
        GridLayout bl = new GridLayout(2, false);
        bl.marginWidth = 0;
        bl.horizontalSpacing = 8;
        buttons.setLayout(bl);
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        Button applyButton = new Button(buttons, SWT.PUSH);
        applyButton.setText("  Apply Edits  ");
        applyButton.addListener(SWT.Selection, e -> {
            result = editor.getText();
            shell.close();
        });

        Button cancelButton = new Button(buttons, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.addListener(SWT.Selection, e -> {
            result = null;
            shell.close();
        });

        // Center on parent
        shell.setLocation(
                getParent().getBounds().x + (getParent().getBounds().width - shell.getSize().x) / 2,
                getParent().getBounds().y + (getParent().getBounds().height - shell.getSize().y) / 2);

        shell.open();
        Display display = getParent().getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        if (codeFont != null && !codeFont.isDisposed()) {
            codeFont.dispose();
        }

        return result;
    }

    private Font getCodeFont(Display display) {
        String[] candidates = { "Menlo", "Consolas", "Courier New", "Courier" };
        for (String name : candidates) {
            try {
                codeFont = new Font(display, name, 12, SWT.NORMAL);
                if (codeFont.getFontData().length > 0) return codeFont;
            } catch (Exception e) {
                // Try next
            }
        }
        return display.getSystemFont();
    }
}
