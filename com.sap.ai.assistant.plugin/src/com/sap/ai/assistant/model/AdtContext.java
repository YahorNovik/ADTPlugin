package com.sap.ai.assistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures the current ADT (ABAP Development Tools) editor context, including
 * the object being edited, source code, cursor position, selection, and any
 * diagnostic errors.
 */
public class AdtContext {

    private String objectUri;
    private String objectName;
    private String objectType;
    private String sourceCode;
    private int cursorLine;
    private int cursorColumn;
    private String selectedText;
    private List<String> errors;

    /**
     * Creates a new empty ADT context.
     */
    public AdtContext() {
        this.errors = new ArrayList<>();
    }

    /**
     * Full constructor.
     *
     * @param objectUri    the ADT URI of the object
     * @param objectName   the display name of the object
     * @param objectType   the ADT object type (e.g. "CLAS", "PROG", "FUGR")
     * @param sourceCode   the full source code of the object
     * @param cursorLine   the 1-based line number of the cursor
     * @param cursorColumn the 1-based column number of the cursor
     * @param selectedText the currently selected text (may be null)
     * @param errors       the list of diagnostic errors/warnings
     */
    public AdtContext(String objectUri, String objectName, String objectType, String sourceCode,
                      int cursorLine, int cursorColumn, String selectedText, List<String> errors) {
        this.objectUri = objectUri;
        this.objectName = objectName;
        this.objectType = objectType;
        this.sourceCode = sourceCode;
        this.cursorLine = cursorLine;
        this.cursorColumn = cursorColumn;
        this.selectedText = selectedText;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    // -- Getters / Setters -------------------------------------------------------

    public String getObjectUri() {
        return objectUri;
    }

    public void setObjectUri(String objectUri) {
        this.objectUri = objectUri;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public int getCursorLine() {
        return cursorLine;
    }

    public void setCursorLine(int cursorLine) {
        this.cursorLine = cursorLine;
    }

    public int getCursorColumn() {
        return cursorColumn;
    }

    public void setCursorColumn(int cursorColumn) {
        this.cursorColumn = cursorColumn;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "AdtContext{objectUri='" + objectUri + "'"
                + ", objectName='" + objectName + "'"
                + ", objectType='" + objectType + "'"
                + ", cursorLine=" + cursorLine
                + ", cursorColumn=" + cursorColumn
                + ", errors=" + (errors != null ? errors.size() : 0)
                + "}";
    }
}
