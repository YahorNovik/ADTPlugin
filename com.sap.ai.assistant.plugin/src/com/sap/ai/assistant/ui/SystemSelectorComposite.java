package com.sap.ai.assistant.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.sap.ai.assistant.model.SapSystemConnection;
import com.sap.ai.assistant.sap.AdtConnectionManager;

/**
 * A composite containing a label ("SAP System:") and a dropdown combo
 * populated with discovered and manually registered SAP system connections.
 * <p>
 * The last entry in the combo is always "Add Manual..." which, when
 * selected, opens a dialog to register a new SAP system connection.
 * </p>
 */
public class SystemSelectorComposite extends Composite {

    private static final String ADD_MANUAL_LABEL = "Add Manual...";

    private final Combo combo;
    private final AdtConnectionManager connectionManager;
    private final List<SapSystemConnection> systems;

    /**
     * Create the system selector.
     *
     * @param parent the parent composite
     * @param style  SWT style bits
     */
    public SystemSelectorComposite(Composite parent, int style) {
        super(parent, style);
        this.connectionManager = new AdtConnectionManager();
        this.systems = new ArrayList<>();

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        setLayout(layout);

        Label label = new Label(this, SWT.NONE);
        label.setText("SAP System:");

        combo = new Combo(this, SWT.READ_ONLY | SWT.DROP_DOWN);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        refreshSystems();

        // Handle selection of the "Add Manual..." entry
        combo.addListener(SWT.Selection, e -> {
            int idx = combo.getSelectionIndex();
            if (idx == combo.getItemCount() - 1) {
                // Last item is "Add Manual..."
                handleAddManual();
            }
        });
    }

    /**
     * Returns the currently selected SAP system connection.
     *
     * @return the selected connection, or {@code null} if nothing valid is selected
     */
    public SapSystemConnection getSelectedSystem() {
        int idx = combo.getSelectionIndex();
        if (idx < 0 || idx >= systems.size()) {
            return null;
        }
        return systems.get(idx);
    }

    /**
     * Enable or disable the combo. Typically called to freeze selection
     * after the first message is sent.
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (combo != null && !combo.isDisposed()) {
            combo.setEnabled(enabled);
        }
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * Re-discover systems and rebuild the combo items.
     */
    private void refreshSystems() {
        systems.clear();
        combo.removeAll();

        List<SapSystemConnection> discovered = connectionManager.discoverSystems();
        systems.addAll(discovered);

        for (SapSystemConnection sys : systems) {
            String displayText = sys.getProjectName() + " (" + sys.getHost() + ")";
            combo.add(displayText);
        }

        // Sentinel entry
        combo.add(ADD_MANUAL_LABEL);

        // Auto-select the first real system if available
        if (!systems.isEmpty()) {
            combo.select(0);
        }
    }

    /**
     * Show dialogs to collect host, port, client, user, and password for
     * a manual SAP system connection.
     */
    private void handleAddManual() {
        Shell shell = getShell();

        // Host
        InputDialog hostDialog = new InputDialog(
                shell, "Add SAP System",
                "Enter the SAP system hostname (e.g. myhost.sap.corp):",
                "", null);
        if (hostDialog.open() != Window.OK) {
            resetComboSelection();
            return;
        }
        String host = hostDialog.getValue().trim();
        if (host.isEmpty()) {
            resetComboSelection();
            return;
        }

        // Port
        InputDialog portDialog = new InputDialog(
                shell, "Add SAP System",
                "Enter the HTTP(S) port (e.g. 44300):",
                "443", input -> {
                    try {
                        int p = Integer.parseInt(input);
                        return (p > 0 && p <= 65535) ? null : "Port must be 1-65535";
                    } catch (NumberFormatException ex) {
                        return "Invalid number";
                    }
                });
        if (portDialog.open() != Window.OK) {
            resetComboSelection();
            return;
        }
        int port = Integer.parseInt(portDialog.getValue().trim());

        // Client
        InputDialog clientDialog = new InputDialog(
                shell, "Add SAP System",
                "Enter the SAP client number (e.g. 100):",
                "100", null);
        if (clientDialog.open() != Window.OK) {
            resetComboSelection();
            return;
        }
        String client = clientDialog.getValue().trim();

        // User
        InputDialog userDialog = new InputDialog(
                shell, "Add SAP System",
                "Enter the SAP user name:",
                "", null);
        if (userDialog.open() != Window.OK) {
            resetComboSelection();
            return;
        }
        String user = userDialog.getValue().trim();

        // Password
        InputDialog passDialog = new InputDialog(
                shell, "Add SAP System",
                "Enter the SAP password:",
                "", null) {
            @Override
            protected int getInputTextStyle() {
                return SWT.SINGLE | SWT.BORDER | SWT.PASSWORD;
            }
        };
        if (passDialog.open() != Window.OK) {
            resetComboSelection();
            return;
        }
        String password = passDialog.getValue();

        // Register and refresh
        String name = host + ":" + port + " [" + client + "]";
        connectionManager.addManualSystem(name, host, port, client, user, password);
        refreshSystems();

        // Select the newly added system (last in list before sentinel)
        if (systems.size() > 0) {
            combo.select(systems.size() - 1);
        }
    }

    /**
     * Reset combo selection to the first real system (or deselect).
     */
    private void resetComboSelection() {
        if (!systems.isEmpty()) {
            combo.select(0);
        } else {
            combo.deselectAll();
        }
    }
}
