package com.sap.ai.assistant.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.sap.ai.assistant.model.AdtContext;

/**
 * A composite that lets the user select which open editor contexts to include
 * with their message. Shows a "+ Add Context" button and removable chips for
 * each selected editor.
 */
public class ContextSelectorComposite extends Composite {

    /** Available contexts from open editors (keyed by objectName for dedup). */
    private final Map<String, AdtContext> availableContexts = new LinkedHashMap<>();

    /** Currently selected contexts (keyed by objectName). */
    private final Map<String, AdtContext> selectedContexts = new LinkedHashMap<>();

    private Button addButton;
    private Composite chipsArea;
    private Shell dropdownShell;

    public ContextSelectorComposite(Composite parent, int style) {
        super(parent, style);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginTop = 2;
        layout.marginBottom = 2;
        layout.marginLeft = 8;
        layout.marginRight = 4;
        layout.spacing = 4;
        layout.wrap = true;
        layout.center = true;
        setLayout(layout);

        addButton = new Button(this, SWT.PUSH);
        addButton.setText("+ Context");
        addButton.setToolTipText("Select open editors to include as context");
        addButton.addListener(SWT.Selection, e -> showDropdown());

        chipsArea = new Composite(this, SWT.NONE);
        RowLayout chipsLayout = new RowLayout(SWT.HORIZONTAL);
        chipsLayout.spacing = 4;
        chipsLayout.marginTop = 0;
        chipsLayout.marginBottom = 0;
        chipsLayout.marginLeft = 0;
        chipsLayout.marginRight = 0;
        chipsLayout.wrap = true;
        chipsLayout.center = true;
        chipsArea.setLayout(chipsLayout);
    }

    /**
     * Updates the list of available contexts (all open editors).
     * Preserves current selections that are still available.
     */
    public void setAvailableContexts(List<AdtContext> contexts) {
        availableContexts.clear();
        if (contexts != null) {
            for (AdtContext ctx : contexts) {
                if (ctx.getObjectName() != null) {
                    availableContexts.put(ctx.getObjectName(), ctx);
                }
            }
        }
        // Remove selections that are no longer available
        selectedContexts.keySet().retainAll(availableContexts.keySet());
        rebuildChips();
    }

    /**
     * Returns the currently selected contexts.
     */
    public List<AdtContext> getSelectedContexts() {
        // Return fresh contexts from availableContexts (more up-to-date source code)
        List<AdtContext> result = new ArrayList<>();
        for (String name : selectedContexts.keySet()) {
            AdtContext fresh = availableContexts.get(name);
            if (fresh != null) {
                result.add(fresh);
            } else {
                result.add(selectedContexts.get(name));
            }
        }
        return result;
    }

    /**
     * Programmatically adds a context as selected.
     */
    public void addContext(AdtContext ctx) {
        if (ctx == null || ctx.getObjectName() == null) return;
        if (!selectedContexts.containsKey(ctx.getObjectName())) {
            selectedContexts.put(ctx.getObjectName(), ctx);
            rebuildChips();
        }
    }

    /**
     * Removes a context from selection.
     */
    public void removeContext(String objectName) {
        if (selectedContexts.remove(objectName) != null) {
            rebuildChips();
        }
    }

    /**
     * Clears all selections.
     */
    public void clearSelections() {
        selectedContexts.clear();
        rebuildChips();
    }

    // ------------------------------------------------------------------
    // Dropdown popup
    // ------------------------------------------------------------------

    private void showDropdown() {
        // Toggle: if already open, close it
        if (dropdownShell != null && !dropdownShell.isDisposed()) {
            dropdownShell.dispose();
            dropdownShell = null;
            return;
        }

        if (availableContexts.isEmpty()) return;

        dropdownShell = new Shell(getShell(), SWT.NO_TRIM | SWT.ON_TOP | SWT.BORDER);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 6;
        gl.marginHeight = 6;
        gl.verticalSpacing = 4;
        dropdownShell.setLayout(gl);

        Color bg = getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
        dropdownShell.setBackground(bg);

        Label header = new Label(dropdownShell, SWT.NONE);
        header.setText("Open editors:");
        header.setBackground(bg);
        header.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));

        for (Map.Entry<String, AdtContext> entry : availableContexts.entrySet()) {
            String name = entry.getKey();
            AdtContext ctx = entry.getValue();

            Button cb = new Button(dropdownShell, SWT.CHECK);
            String label = name;
            if (ctx.getObjectType() != null) {
                label += "  [" + ctx.getObjectType() + "]";
            }
            cb.setText(label);
            cb.setBackground(bg);
            cb.setSelection(selectedContexts.containsKey(name));
            cb.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            cb.addListener(SWT.Selection, e -> {
                if (cb.getSelection()) {
                    selectedContexts.put(name, ctx);
                } else {
                    selectedContexts.remove(name);
                }
                rebuildChips();
            });
        }

        // Position below the button
        Point buttonLoc = addButton.toDisplay(0, addButton.getSize().y);
        dropdownShell.setLocation(buttonLoc.x, buttonLoc.y + 2);
        dropdownShell.pack();

        // Ensure minimum width
        Point size = dropdownShell.getSize();
        if (size.x < 200) {
            dropdownShell.setSize(200, size.y);
        }

        dropdownShell.setVisible(true);

        // Close on click outside: use a display-level mouse filter
        final Shell popup = dropdownShell;
        final Listener outsideClickFilter = new Listener() {
            @Override
            public void handleEvent(Event event) {
                if (popup.isDisposed()) {
                    getDisplay().removeFilter(SWT.MouseDown, this);
                    return;
                }
                // Check if the click is inside the dropdown shell
                Control target = getDisplay().getCursorControl();
                if (target != null) {
                    Shell targetShell = target.getShell();
                    if (targetShell == popup) {
                        return; // Click inside popup — do nothing
                    }
                }
                // Click outside — close the popup
                getDisplay().removeFilter(SWT.MouseDown, this);
                if (!popup.isDisposed()) {
                    popup.dispose();
                }
                if (dropdownShell == popup) {
                    dropdownShell = null;
                }
            }
        };
        getDisplay().addFilter(SWT.MouseDown, outsideClickFilter);

        // Also close on Deactivate (covers Alt-Tab, etc.)
        popup.addListener(SWT.Deactivate, e -> {
            getDisplay().asyncExec(() -> {
                getDisplay().removeFilter(SWT.MouseDown, outsideClickFilter);
                if (!popup.isDisposed()) {
                    popup.dispose();
                }
                if (dropdownShell == popup) {
                    dropdownShell = null;
                }
            });
        });

        // Clean up filter on dispose
        popup.addListener(SWT.Dispose, e -> {
            getDisplay().removeFilter(SWT.MouseDown, outsideClickFilter);
            if (dropdownShell == popup) {
                dropdownShell = null;
            }
        });
    }

    // ------------------------------------------------------------------
    // Chip rendering
    // ------------------------------------------------------------------

    private void rebuildChips() {
        if (chipsArea == null || chipsArea.isDisposed()) return;

        // Dispose old chips
        for (Control child : chipsArea.getChildren()) {
            child.dispose();
        }

        // Create a chip for each selected context
        for (Map.Entry<String, AdtContext> entry : selectedContexts.entrySet()) {
            String name = entry.getKey();
            AdtContext ctx = entry.getValue();
            createChip(chipsArea, name, ctx);
        }

        chipsArea.requestLayout();
        requestLayout();
    }

    private void createChip(Composite parent, String objectName, AdtContext ctx) {
        Composite chip = new Composite(parent, SWT.BORDER);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.marginTop = 1;
        rl.marginBottom = 1;
        rl.marginLeft = 4;
        rl.marginRight = 2;
        rl.spacing = 2;
        rl.center = true;
        chip.setLayout(rl);

        Color chipBg = getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
        chip.setBackground(chipBg);

        Label nameLabel = new Label(chip, SWT.NONE);
        String text = objectName;
        if (ctx.getObjectType() != null) {
            text += " [" + ctx.getObjectType() + "]";
        }
        nameLabel.setText(text);
        nameLabel.setBackground(chipBg);
        nameLabel.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));

        Label closeLabel = new Label(chip, SWT.NONE);
        closeLabel.setText(" x");
        closeLabel.setBackground(chipBg);
        closeLabel.setForeground(getDisplay().getSystemColor(SWT.COLOR_DARK_RED));
        closeLabel.setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        closeLabel.addListener(SWT.MouseDown, e -> removeContext(objectName));
    }
}
