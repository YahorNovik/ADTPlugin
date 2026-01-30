package com.sap.ai.assistant.context;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;

import com.sap.ai.assistant.model.AdtContext;

/**
 * Tracks the currently active Eclipse editor and maintains an up-to-date
 * {@link AdtContext} reflecting the editor's content, cursor position,
 * and diagnostic markers.
 * <p>
 * Register an instance of this class as an {@link IPartListener2} via
 * {@code IPartService.addPartListener()} to receive editor activation events.
 * </p>
 */
public class EditorContextTracker implements IPartListener2 {

    /** The latest extracted context. Volatile for safe cross-thread reads. */
    private volatile AdtContext currentContext;

    /**
     * Returns the most recently extracted editor context.
     *
     * @return the current {@link AdtContext}, or {@code null} if no editor is active
     */
    public AdtContext getCurrentContext() {
        return currentContext;
    }

    // ------------------------------------------------------------------
    // IPartListener2 -- only partActivated and partInputChanged matter
    // ------------------------------------------------------------------

    @Override
    public void partActivated(IWorkbenchPartReference partRef) {
        updateContextFromPart(partRef);
    }

    @Override
    public void partInputChanged(IWorkbenchPartReference partRef) {
        updateContextFromPart(partRef);
    }

    @Override
    public void partBroughtToTop(IWorkbenchPartReference partRef) {
        // Not relevant
    }

    @Override
    public void partClosed(IWorkbenchPartReference partRef) {
        // When the active editor is closed, clear context
        if (partRef.getPart(false) instanceof IEditorPart) {
            currentContext = null;
        }
    }

    @Override
    public void partDeactivated(IWorkbenchPartReference partRef) {
        // Not relevant -- we update on activation
    }

    @Override
    public void partHidden(IWorkbenchPartReference partRef) {
        // Not relevant
    }

    @Override
    public void partOpened(IWorkbenchPartReference partRef) {
        // Not relevant
    }

    @Override
    public void partVisible(IWorkbenchPartReference partRef) {
        // Not relevant
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * If the referenced part is an editor, extract the ADT context from it.
     */
    private void updateContextFromPart(IWorkbenchPartReference partRef) {
        try {
            if (partRef == null) {
                return;
            }
            org.eclipse.ui.IWorkbenchPart part = partRef.getPart(false);
            if (part instanceof IEditorPart) {
                IEditorPart editor = (IEditorPart) part;
                AdtContext ctx = AdtEditorHelper.extractContext(editor);
                if (ctx != null) {
                    currentContext = ctx;
                }
            }
        } catch (Exception e) {
            // Never let listener exceptions propagate to the platform
            System.err.println("EditorContextTracker: " + e.getMessage());
        }
    }
}
