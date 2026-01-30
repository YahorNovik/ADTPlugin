package com.sap.ai.assistant.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.sap.ai.assistant.ui.AiAssistantView;

/**
 * Handler for the "Open AI Assistant" command (Ctrl+Shift+A / Cmd+Shift+A).
 * Opens the AI Assistant view and focuses the chat input field.
 */
public class OpenAiViewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            IWorkbenchPage page = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();
            AiAssistantView view = (AiAssistantView) page.showView(
                    AiAssistantView.VIEW_ID);
            view.setFocus();
        } catch (Exception e) {
            System.err.println("OpenAiViewHandler: " + e.getMessage());
        }
        return null;
    }
}
