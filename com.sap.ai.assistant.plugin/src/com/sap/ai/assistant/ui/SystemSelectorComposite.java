package com.sap.ai.assistant.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.sap.ai.assistant.Activator;
import com.sap.ai.assistant.model.SavedSapSystem;
import com.sap.ai.assistant.model.SapSystemConnection;
import com.sap.ai.assistant.preferences.PreferenceConstants;
import com.sap.ai.assistant.sap.AdtConnectionManager;

/**
 * A composite containing a label ("SAP System:"), a dropdown combo
 * populated with discovered and manually registered SAP system connections,
 * and a Remove button for deleting saved manual systems.
 * <p>
 * The last entry in the combo is always "Add Manual..." which, when
 * selected, opens a dialog to register a new SAP system connection.
 * Manually added systems are automatically persisted to the Eclipse
 * preference store and restored on subsequent launches.
 * </p>
 */
public class SystemSelectorComposite extends Composite {

    private static final String ADD_MANUAL_LABEL = "Add Manual...";

    private final Combo combo;
    private final Button removeButton;
    private final AdtConnectionManager connectionManager;
    private final List<SapSystemConnection> systems;

    /** Number of ADT-discovered systems (these cannot be removed). */
    private int discoveredCount;

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

        // Load previously saved systems from preferences
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        String savedJson = store.getString(PreferenceConstants.SAP_SAVED_SYSTEMS);
        connectionManager.loadSavedSystems(savedJson);

        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        setLayout(layout);

        Label label = new Label(this, SWT.NONE);
        label.setText("SAP System:");

        combo = new Combo(this, SWT.READ_ONLY | SWT.DROP_DOWN);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        removeButton = new Button(this, SWT.PUSH);
        removeButton.setText("Remove");
        removeButton.setEnabled(false);
        removeButton.addListener(SWT.Selection, e -> handleRemoveSystem());

        refreshSystems();

        // Handle selection of the "Add Manual..." entry and update remove button
        combo.addListener(SWT.Selection, e -> {
            int idx = combo.getSelectionIndex();
            if (idx == combo.getItemCount() - 1) {
                // Last item is "Add Manual..."
                handleAddManual();
            }
            updateRemoveButton();
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
     * Enable or disable the combo and remove button. Typically called to
     * freeze selection after the first message is sent.
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (combo != null && !combo.isDisposed()) {
            combo.setEnabled(enabled);
        }
        if (removeButton != null && !removeButton.isDisposed()) {
            removeButton.setEnabled(enabled && isManualSystemSelected());
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
        // discoverSystems() returns discovered + manual systems combined.
        // We need to know the boundary so we can tell which are removable.
        // Discovered systems come first, manual systems are appended.
        int totalDiscovered = discovered.size() - connectionManager.getManualSystems().size();
        discoveredCount = Math.max(0, totalDiscovered);

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

        updateRemoveButton();
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

        // Register, persist, and refresh
        String name = host + ":" + port + " [" + client + "]";
        connectionManager.addManualSystem(name, host, port, client, user, password);
        persistSavedSystems();
        refreshSystems();

        // Select the newly added system (last in list before sentinel)
        if (systems.size() > 0) {
            combo.select(systems.size() - 1);
        }
        updateRemoveButton();
    }

    /**
     * Remove the currently selected manual system after user confirmation.
     */
    private void handleRemoveSystem() {
        int idx = combo.getSelectionIndex();
        if (idx < 0 || idx < discoveredCount || idx >= systems.size()) {
            return;
        }

        SapSystemConnection sys = systems.get(idx);
        boolean confirmed = MessageDialog.openConfirm(getShell(),
                "Remove SAP System",
                "Remove saved system \"" + sys.getProjectName() + "\"?");
        if (!confirmed) {
            return;
        }

        int manualIdx = idx - discoveredCount;
        connectionManager.removeManualSystem(manualIdx);
        persistSavedSystems();
        refreshSystems();
    }

    /**
     * Persists the current manual systems list to the Eclipse preference store.
     */
    private void persistSavedSystems() {
        List<SapSystemConnection> manual = connectionManager.getManualSystems();
        List<SavedSapSystem> toSave = new ArrayList<>();
        for (SapSystemConnection conn : manual) {
            toSave.add(SavedSapSystem.fromConnection(conn));
        }
        String json = SavedSapSystem.toJson(toSave);
        Activator.getDefault().getPreferenceStore()
                .setValue(PreferenceConstants.SAP_SAVED_SYSTEMS, json);
    }

    /**
     * Returns whether the currently selected system is a manually added one
     * (and therefore removable).
     */
    private boolean isManualSystemSelected() {
        int idx = combo.getSelectionIndex();
        return idx >= discoveredCount && idx < systems.size();
    }

    /**
     * Enable/disable the remove button based on the current selection.
     */
    private void updateRemoveButton() {
        if (removeButton != null && !removeButton.isDisposed()) {
            removeButton.setEnabled(isManualSystemSelected());
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
        updateRemoveButton();
    }
}
