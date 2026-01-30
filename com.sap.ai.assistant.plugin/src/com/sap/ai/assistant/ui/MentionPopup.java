package com.sap.ai.assistant.ui;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/**
 * A lightweight popup that appears when the user types {@code @} in the
 * chat input, offering mention completions like {@code @errors},
 * {@code @selection}, {@code @source}, or a typed SAP object name.
 */
public class MentionPopup {

    private Shell popup;
    private List list;
    private final StyledText inputText;
    private final Consumer<String> onSelect;

    private static final String[] CATEGORIES = {
        "@errors — current editor errors",
        "@selection — selected code",
        "@source — full source code"
    };
    private static final String[] CATEGORY_VALUES = {
        "@errors", "@selection", "@source"
    };

    public MentionPopup(StyledText inputText, Consumer<String> onSelect) {
        this.inputText = inputText;
        this.onSelect = onSelect;
    }

    /**
     * Shows (or updates) the popup, filtering by the text after {@code @}.
     *
     * @param filterText the text typed after the {@code @} character
     */
    public void show(String filterText) {
        if (popup != null && !popup.isDisposed()) {
            popup.dispose();
        }

        Shell parentShell = inputText.getShell();
        popup = new Shell(parentShell, SWT.NO_TRIM | SWT.ON_TOP);
        popup.setLayout(new FillLayout());

        list = new List(popup, SWT.SINGLE | SWT.V_SCROLL);

        String filter = filterText != null ? filterText.toLowerCase() : "";

        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORY_VALUES[i].substring(1).startsWith(filter)) {
                list.add(CATEGORIES[i]);
                list.setData(CATEGORIES[i], CATEGORY_VALUES[i]);
            }
        }

        // If user typed something that doesn't match categories, offer as object name
        if (filter.length() > 0) {
            boolean matchesCategory = false;
            for (String cv : CATEGORY_VALUES) {
                if (cv.substring(1).startsWith(filter)) {
                    matchesCategory = true;
                    break;
                }
            }
            if (!matchesCategory) {
                String objectEntry = "@" + filter.toUpperCase() + " (SAP object)";
                list.add(objectEntry);
                list.setData(objectEntry, "@" + filter.toUpperCase());
            }
        }

        if (list.getItemCount() == 0) {
            dispose();
            return;
        }

        list.select(0);
        list.addListener(SWT.DefaultSelection, e -> acceptSelection());

        // Position below caret
        try {
            Point caretLoc = inputText.getLocationAtOffset(inputText.getCaretOffset());
            Point displayPt = inputText.toDisplay(caretLoc);
            popup.setBounds(displayPt.x, displayPt.y + 20, 250, 80);
        } catch (Exception e) {
            popup.setBounds(100, 100, 250, 80);
        }
        popup.setVisible(true);
    }

    /**
     * Accepts the currently selected item and notifies the callback.
     */
    public void acceptSelection() {
        if (list != null && !list.isDisposed() && list.getSelectionIndex() >= 0) {
            String item = list.getItem(list.getSelectionIndex());
            Object value = list.getData(item);
            String mention = value instanceof String ? (String) value : item;
            onSelect.accept(mention);
        }
        dispose();
    }

    /**
     * Moves the selection up or down.
     */
    public void moveSelection(int direction) {
        if (list == null || list.isDisposed()) return;
        int idx = list.getSelectionIndex() + direction;
        if (idx >= 0 && idx < list.getItemCount()) {
            list.select(idx);
        }
    }

    public boolean isVisible() {
        return popup != null && !popup.isDisposed() && popup.isVisible();
    }

    public void dispose() {
        if (popup != null && !popup.isDisposed()) {
            popup.dispose();
        }
        popup = null;
    }
}
