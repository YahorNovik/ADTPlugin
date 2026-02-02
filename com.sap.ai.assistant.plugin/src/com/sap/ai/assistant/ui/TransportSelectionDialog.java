package com.sap.ai.assistant.ui;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.sap.ai.assistant.model.TransportSelection;
import com.sap.ai.assistant.model.TransportSelectionRequest.TransportEntry;

/**
 * Modal dialog for selecting how objects should be transported during the session.
 * <p>
 * Offers three options:
 * <ol>
 *   <li>Save as Local Object ($TMP)</li>
 *   <li>Use an existing open transport request</li>
 *   <li>Create a new transport request</li>
 * </ol>
 * The selection is remembered for the entire chat session.
 * </p>
 */
public class TransportSelectionDialog extends Dialog {

    private final List<TransportEntry> transports;
    private TransportSelection result;

    private Button radioLocal;
    private Button radioExisting;
    private Button radioNew;
    private Combo transportCombo;
    private Text newTransportDesc;

    public TransportSelectionDialog(Shell parentShell, List<TransportEntry> transports) {
        super(parentShell);
        this.transports = transports;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Transport Selection");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 15;
        gl.marginHeight = 15;
        gl.verticalSpacing = 8;
        container.setLayout(gl);

        // Header
        Label header = new Label(container, SWT.WRAP);
        header.setText("Choose how objects should be saved during this session:");
        header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Option 1: Local ($TMP)
        radioLocal = new Button(container, SWT.RADIO);
        radioLocal.setText("Save as Local Object ($TMP)");
        radioLocal.setSelection(true);

        // Option 2: Existing transport
        radioExisting = new Button(container, SWT.RADIO);
        radioExisting.setText("Use Existing Transport:");

        Composite existingRow = new Composite(container, SWT.NONE);
        GridLayout existingLayout = new GridLayout(1, false);
        existingLayout.marginLeft = 20;
        existingLayout.marginWidth = 0;
        existingLayout.marginHeight = 0;
        existingRow.setLayout(existingLayout);
        existingRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        transportCombo = new Combo(existingRow, SWT.READ_ONLY | SWT.DROP_DOWN);
        GridData comboData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        comboData.widthHint = 350;
        transportCombo.setLayoutData(comboData);
        transportCombo.setEnabled(false);

        if (transports != null && !transports.isEmpty()) {
            for (TransportEntry entry : transports) {
                transportCombo.add(entry.toString());
            }
            transportCombo.select(0);
        } else {
            transportCombo.add("(no open transports found)");
            transportCombo.select(0);
        }

        // Option 3: New transport
        radioNew = new Button(container, SWT.RADIO);
        radioNew.setText("Create New Transport:");

        Composite newRow = new Composite(container, SWT.NONE);
        GridLayout newLayout = new GridLayout(2, false);
        newLayout.marginLeft = 20;
        newLayout.marginWidth = 0;
        newLayout.marginHeight = 0;
        newRow.setLayout(newLayout);
        newRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label descLabel = new Label(newRow, SWT.NONE);
        descLabel.setText("Description:");

        newTransportDesc = new Text(newRow, SWT.BORDER);
        GridData descData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        descData.widthHint = 280;
        newTransportDesc.setLayoutData(descData);
        newTransportDesc.setEnabled(false);
        newTransportDesc.setMessage("AI-generated objects");

        // Separator
        Label separator = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Session hint
        Label sessionHint = new Label(container, SWT.NONE);
        sessionHint.setText("This choice will be used for all objects in this session.");
        sessionHint.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));

        // Radio button listeners — enable/disable associated controls
        radioLocal.addListener(SWT.Selection, e -> updateControls());
        radioExisting.addListener(SWT.Selection, e -> updateControls());
        radioNew.addListener(SWT.Selection, e -> updateControls());

        // Clicking on the combo or text field should auto-select the radio
        transportCombo.addListener(SWT.FocusIn, e -> {
            radioLocal.setSelection(false);
            radioExisting.setSelection(true);
            radioNew.setSelection(false);
            updateControls();
        });
        newTransportDesc.addListener(SWT.FocusIn, e -> {
            radioLocal.setSelection(false);
            radioExisting.setSelection(false);
            radioNew.setSelection(true);
            updateControls();
        });

        // Disable "Existing Transport" if none available
        if (transports == null || transports.isEmpty()) {
            radioExisting.setEnabled(false);
            transportCombo.setEnabled(false);
        }

        return area;
    }

    private void updateControls() {
        boolean hasTransports = transports != null && !transports.isEmpty();
        transportCombo.setEnabled(radioExisting.getSelection() && hasTransports);
        newTransportDesc.setEnabled(radioNew.getSelection());
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "OK", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void okPressed() {
        if (radioLocal.getSelection()) {
            result = TransportSelection.local();
        } else if (radioExisting.getSelection()) {
            if (transports == null || transports.isEmpty()) {
                MessageDialog.openError(getShell(), "Transport Selection",
                        "No open transports available. Choose a different option.");
                return;
            }
            int idx = transportCombo.getSelectionIndex();
            if (idx < 0 || idx >= transports.size()) {
                MessageDialog.openError(getShell(), "Transport Selection",
                        "Please select a transport request.");
                return;
            }
            result = TransportSelection.withTransport(transports.get(idx).getNumber());
        } else if (radioNew.getSelection()) {
            String desc = newTransportDesc.getText().trim();
            if (desc.isEmpty()) {
                MessageDialog.openError(getShell(), "Transport Selection",
                        "Please enter a description for the new transport.");
                return;
            }
            result = TransportSelection.newTransport(desc);
        }

        super.okPressed();
    }

    /**
     * Returns the user's transport selection, or {@code null} if cancelled.
     */
    public TransportSelection getResult() {
        return result;
    }
}
