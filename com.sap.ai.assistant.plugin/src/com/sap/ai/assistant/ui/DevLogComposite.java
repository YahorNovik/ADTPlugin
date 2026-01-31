package com.sap.ai.assistant.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.sap.ai.assistant.model.LlmUsage;
import com.sap.ai.assistant.model.RequestLogEntry;
import com.sap.ai.assistant.model.UsageTracker;

/**
 * A collapsible developer log panel showing LLM request/response metadata
 * including token usage, timing, tool call I/O, and errors.
 * <p>
 * The table shows a summary row per LLM request. Selecting a row displays
 * the full detail (LLM text, tool arguments, tool results) in a text pane
 * below the table.
 * </p>
 */
public class DevLogComposite extends Composite {

    private Table logTable;
    private StyledText detailText;
    private Label summaryLabel;
    private UsageTracker tracker;
    private boolean expanded = false;
    private Composite tableContainer;
    private Button toggleButton;
    private final List<RequestLogEntry> entries = new ArrayList<>();

    public DevLogComposite(Composite parent, int style) {
        super(parent, style);
        setLayout(new GridLayout(1, false));
        setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        // Header with toggle + buttons
        Composite header = new Composite(this, SWT.NONE);
        GridLayout headerLayout = new GridLayout(4, false);
        headerLayout.marginHeight = 2;
        headerLayout.marginWidth = 5;
        header.setLayout(headerLayout);
        header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        toggleButton = new Button(header, SWT.PUSH | SWT.FLAT);
        toggleButton.setText("Dev Log");
        toggleButton.addListener(SWT.Selection, e -> toggleExpanded());

        summaryLabel = new Label(header, SWT.NONE);
        summaryLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        summaryLabel.setText("0 requests");

        Button copyBtn = new Button(header, SWT.PUSH | SWT.FLAT);
        copyBtn.setText("Copy");
        copyBtn.addListener(SWT.Selection, e -> copyLog());

        Button clearBtn = new Button(header, SWT.PUSH | SWT.FLAT);
        clearBtn.setText("Clear");
        clearBtn.addListener(SWT.Selection, e -> clearLog());

        // Table container (initially hidden)
        tableContainer = new Composite(this, SWT.NONE);
        tableContainer.setLayout(new GridLayout(1, false));
        GridData tableGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        tableGd.heightHint = 0;
        tableGd.exclude = true;
        tableContainer.setLayoutData(tableGd);

        // Summary table
        logTable = new Table(tableContainer, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        logTable.setHeaderVisible(true);
        logTable.setLinesVisible(true);
        GridData tGd = new GridData(SWT.FILL, SWT.FILL, true, false);
        tGd.heightHint = 110;
        logTable.setLayoutData(tGd);

        addColumn("#", 30);
        addColumn("Time", 65);
        addColumn("Model", 120);
        addColumn("Tokens In", 70);
        addColumn("Tokens Out", 70);
        addColumn("Duration", 65);
        addColumn("Tools", 150);
        addColumn("Status", 60);

        // Detail pane (shows full info for selected row)
        detailText = new StyledText(tableContainer, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        GridData dtGd = new GridData(SWT.FILL, SWT.FILL, true, true);
        dtGd.heightHint = 120;
        detailText.setLayoutData(dtGd);
        detailText.setBackground(getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

        // Show detail when a row is selected
        logTable.addListener(SWT.Selection, e -> showSelectedDetail());
    }

    private void addColumn(String name, int width) {
        TableColumn col = new TableColumn(logTable, SWT.NONE);
        col.setText(name);
        col.setWidth(width);
    }

    public void setTracker(UsageTracker tracker) {
        this.tracker = tracker;
    }

    /**
     * Add a new log entry to the table and update summary.
     * Must be called from the UI thread.
     */
    public void addEntry(RequestLogEntry entry) {
        if (isDisposed() || logTable.isDisposed()) return;

        entries.add(entry);

        TableItem item = new TableItem(logTable, SWT.NONE);
        item.setText(0, String.valueOf(entry.getRoundNumber()));
        item.setText(1, entry.getFormattedTime());
        item.setText(2, entry.getModel());

        LlmUsage usage = entry.getUsage();
        item.setText(3, usage != null ? formatNumber(usage.getInputTokens()) : "-");
        item.setText(4, usage != null ? formatNumber(usage.getOutputTokens()) : "-");
        item.setText(5, entry.getFormattedDuration());
        item.setText(6, entry.getToolNamesString());
        item.setText(7, entry.isError() ? "ERROR" : "OK");

        if (entry.isError()) {
            item.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_RED));
        }

        // Scroll to bottom
        logTable.showItem(item);

        // Auto-select the new row to show its details
        logTable.setSelection(logTable.getItemCount() - 1);
        showSelectedDetail();

        updateSummary();
    }

    public void updateSummary() {
        if (isDisposed() || summaryLabel.isDisposed() || tracker == null) return;

        int requests = tracker.getRequestCount();
        int totalIn = tracker.getTotalInputTokens();
        int totalOut = tracker.getTotalOutputTokens();
        summaryLabel.setText(requests + " req | "
                + formatNumber(totalIn) + " in / "
                + formatNumber(totalOut) + " out");
        summaryLabel.getParent().layout(true);
    }

    private void showSelectedDetail() {
        if (detailText == null || detailText.isDisposed()) return;
        int idx = logTable.getSelectionIndex();
        if (idx >= 0 && idx < entries.size()) {
            detailText.setText(entries.get(idx).toDetailString());
        } else {
            detailText.setText("");
        }
    }

    private void toggleExpanded() {
        expanded = !expanded;
        GridData gd = (GridData) tableContainer.getLayoutData();
        gd.exclude = !expanded;
        gd.heightHint = expanded ? 260 : 0;
        tableContainer.setVisible(expanded);
        toggleButton.setText(expanded ? "Dev Log [-]" : "Dev Log");
        getParent().layout(true, true);
    }

    private void copyLog() {
        if (tracker == null) return;
        Clipboard clipboard = new Clipboard(Display.getCurrent());
        clipboard.setContents(
                new Object[]{ tracker.toText() },
                new Transfer[]{ TextTransfer.getInstance() });
        clipboard.dispose();
    }

    public void clearLog() {
        if (tracker != null) tracker.clear();
        entries.clear();
        logTable.removeAll();
        if (detailText != null && !detailText.isDisposed()) {
            detailText.setText("");
        }
        updateSummary();
    }

    private String formatNumber(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1000000) return String.format("%,.0fK", n / 1000.0);
        return String.format("%,.1fM", n / 1000000.0);
    }
}
