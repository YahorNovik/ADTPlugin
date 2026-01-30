package com.sap.ai.assistant.context;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.sap.ai.assistant.model.AdtContext;

/**
 * Static utility that extracts an {@link AdtContext} from an Eclipse editor.
 * <p>
 * All ADT-specific API access is wrapped in try/catch so the plug-in
 * compiles and runs without the ADT SDK on the classpath.
 * </p>
 */
public final class AdtEditorHelper {

    private AdtEditorHelper() {
        // Utility class -- no instances
    }

    /**
     * Extract the current ADT context from the given editor part.
     *
     * @param editor the active editor (may be {@code null})
     * @return a populated {@link AdtContext}, or {@code null} if nothing useful
     *         could be extracted
     */
    public static AdtContext extractContext(IEditorPart editor) {
        if (editor == null) {
            return null;
        }

        AdtContext ctx = new AdtContext();
        IEditorInput input = editor.getEditorInput();

        // ---- Object name / type / URI from editor input ----
        if (input != null) {
            ctx.setObjectName(extractObjectName(input));
            ctx.setObjectType(extractObjectType(input));
            ctx.setObjectUri(extractObjectUri(input));
        }

        // ---- Source code from IDocument ----
        IDocument document = getDocument(editor);
        if (document != null) {
            ctx.setSourceCode(document.get());
        }

        // ---- Cursor position & selection from ITextSelection ----
        extractSelectionInfo(editor, ctx);

        // ---- Errors from IMarker ----
        ctx.setErrors(extractErrors(editor));

        return ctx;
    }

    // ------------------------------------------------------------------
    // Object metadata extraction
    // ------------------------------------------------------------------

    /**
     * Derive the object name from the editor input title or tooltip.
     */
    private static String extractObjectName(IEditorInput input) {
        // The editor title is normally the object name
        String name = input.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        // Fall back to tooltip
        String tooltip = input.getToolTipText();
        if (tooltip != null && !tooltip.isEmpty()) {
            // Tooltip may be a path -- take the last segment
            int lastSlash = tooltip.lastIndexOf('/');
            return lastSlash >= 0 ? tooltip.substring(lastSlash + 1) : tooltip;
        }
        return null;
    }

    /**
     * Attempt to determine the ABAP object type from the editor input.
     * Tries ADT SDK adapter first, then falls back to heuristics on the name/tooltip.
     */
    private static String extractObjectType(IEditorInput input) {
        // Try ADT SDK IAdtObjectReference via adapter
        try {
            Class<?> refClass = Class.forName("com.sap.adt.tools.core.model.adtcore.IAdtObjectReference");
            Object ref = input.getAdapter(refClass);
            if (ref != null) {
                java.lang.reflect.Method getType = ref.getClass().getMethod("getType");
                Object typeObj = getType.invoke(ref);
                if (typeObj != null) {
                    return typeObj.toString();
                }
            }
        } catch (ClassNotFoundException e) {
            // ADT SDK not available
        } catch (Exception e) {
            // Reflection failed -- continue with fallback
        }

        // Heuristic: inspect tooltip path segments
        String tooltip = input.getToolTipText();
        if (tooltip != null) {
            String upper = tooltip.toUpperCase();
            if (upper.contains("/PROGRAMS/")) return "PROG";
            if (upper.contains("/CLASSES/"))  return "CLAS";
            if (upper.contains("/INTERFACES/")) return "INTF";
            if (upper.contains("/FUNCTION_GROUPS/")) return "FUGR";
        }
        return null;
    }

    /**
     * Attempt to extract the ADT URI from the editor input via the ADT SDK adapter.
     */
    private static String extractObjectUri(IEditorInput input) {
        try {
            Class<?> refClass = Class.forName("com.sap.adt.tools.core.model.adtcore.IAdtObjectReference");
            Object ref = input.getAdapter(refClass);
            if (ref != null) {
                java.lang.reflect.Method getUri = ref.getClass().getMethod("getUri");
                Object uriObj = getUri.invoke(ref);
                if (uriObj != null) {
                    return uriObj.toString();
                }
            }
        } catch (ClassNotFoundException e) {
            // ADT SDK not available
        } catch (Exception e) {
            // Reflection failed
        }

        // Fallback: use tooltip as a pseudo-URI
        String tooltip = input.getToolTipText();
        return tooltip;
    }

    // ------------------------------------------------------------------
    // Document / selection helpers
    // ------------------------------------------------------------------

    /**
     * Retrieve the {@link IDocument} from the editor.
     */
    private static IDocument getDocument(IEditorPart editor) {
        // Direct ITextEditor access
        if (editor instanceof ITextEditor) {
            ITextEditor textEditor = (ITextEditor) editor;
            if (textEditor.getDocumentProvider() != null
                    && textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput()) != null) {
                return textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
            }
        }
        // Adapter access
        ITextEditor adapted = editor.getAdapter(ITextEditor.class);
        if (adapted != null && adapted.getDocumentProvider() != null) {
            return adapted.getDocumentProvider().getDocument(adapted.getEditorInput());
        }
        return null;
    }

    /**
     * Extract cursor line, cursor column, and selected text from the editor's
     * current selection.
     */
    private static void extractSelectionInfo(IEditorPart editor, AdtContext ctx) {
        try {
            ISelectionProvider sp = editor.getSite().getSelectionProvider();
            if (sp == null) {
                return;
            }
            ISelection selection = sp.getSelection();
            if (selection instanceof ITextSelection) {
                ITextSelection ts = (ITextSelection) selection;
                // ITextSelection lines are 0-based; AdtContext uses 1-based
                ctx.setCursorLine(ts.getStartLine() + 1);
                ctx.setCursorColumn(ts.getOffset() + 1);
                String text = ts.getText();
                if (text != null && !text.isEmpty()) {
                    ctx.setSelectedText(text);
                }
            }
        } catch (Exception e) {
            // Selection retrieval failed -- leave defaults
        }
    }

    // ------------------------------------------------------------------
    // Error markers
    // ------------------------------------------------------------------

    /**
     * Collect error/warning markers from the resource associated with the editor.
     */
    private static List<String> extractErrors(IEditorPart editor) {
        List<String> errors = new ArrayList<>();
        try {
            IResource resource = editor.getEditorInput().getAdapter(IResource.class);
            if (resource != null && resource.exists()) {
                IMarker[] markers = resource.findMarkers(
                        IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
                for (IMarker marker : markers) {
                    int severity = marker.getAttribute(IMarker.SEVERITY, -1);
                    if (severity == IMarker.SEVERITY_ERROR || severity == IMarker.SEVERITY_WARNING) {
                        String msg = marker.getAttribute(IMarker.MESSAGE, "");
                        int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
                        String prefix = severity == IMarker.SEVERITY_ERROR ? "ERROR" : "WARNING";
                        if (line > 0) {
                            errors.add(prefix + " [line " + line + "]: " + msg);
                        } else {
                            errors.add(prefix + ": " + msg);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Marker retrieval failed -- return empty list
        }
        return errors;
    }
}
