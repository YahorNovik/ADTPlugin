package com.sap.ai.assistant.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.sap.ai.assistant.context.AdtEditorHelper;
import com.sap.ai.assistant.model.AdtContext;
import com.sap.ai.assistant.ui.AiAssistantView;

/**
 * Handler for the "AI Fix Errors" command (Ctrl+Shift+E / Cmd+Shift+E).
 * Captures errors from the active editor and sends them to the AI agent.
 */
public class FixErrorsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            IEditorPart editor = HandlerUtil.getActiveEditor(event);
            if (editor == null) return null;

            AdtContext context = AdtEditorHelper.extractContext(editor);
            if (context == null || context.getObjectName() == null) return null;

            List<String> errors = context.getErrors();
            if (errors == null || errors.isEmpty()) return null;

            String message = buildFixErrorsMessage(context);

            IWorkbenchPage page = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();
            AiAssistantView view = (AiAssistantView) page.showView(
                    AiAssistantView.VIEW_ID);
            view.sendMessage(message);

        } catch (Exception e) {
            System.err.println("FixErrorsHandler: " + e.getMessage());
        }
        return null;
    }

    private String buildFixErrorsMessage(AdtContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("FIX ERRORS REQUEST: Please fix all errors in \"")
          .append(context.getObjectName()).append("\".\n\n");

        sb.append("Current errors:\n");
        for (String error : context.getErrors()) {
            sb.append("- ").append(error).append("\n");
        }

        sb.append("\nPlease:\n");
        sb.append("1. Read the current source using sap_get_source\n");
        sb.append("2. Fix all listed errors\n");
        sb.append("3. Write the corrected source using sap_write_and_check\n");
        sb.append("4. Verify with sap_syntax_check\n");
        return sb.toString();
    }
}
