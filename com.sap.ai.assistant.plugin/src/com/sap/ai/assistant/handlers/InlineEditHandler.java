package com.sap.ai.assistant.handlers;

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
import com.sap.ai.assistant.ui.InlineEditDialog;

/**
 * Handler for the "AI Inline Edit" command (Ctrl+Shift+K / Cmd+Shift+K).
 * <p>
 * Captures the current editor selection, opens a dialog asking the user
 * what change they want, then sends an enriched prompt to the AI agent.
 * </p>
 */
public class InlineEditHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            // 1. Get active editor context
            IEditorPart editor = HandlerUtil.getActiveEditor(event);
            if (editor == null) return null;

            AdtContext context = AdtEditorHelper.extractContext(editor);
            if (context == null) return null;

            String selectedText = context.getSelectedText();
            if (selectedText == null || selectedText.trim().isEmpty()) {
                selectedText = null; // will still allow edit of entire object
            }

            // 2. Show inline edit dialog
            InlineEditDialog dialog = new InlineEditDialog(
                    HandlerUtil.getActiveShell(event),
                    context.getObjectName(),
                    selectedText);
            String instruction = dialog.open();
            if (instruction == null || instruction.trim().isEmpty()) {
                return null; // User cancelled
            }

            // 3. Build enriched message
            String message = buildInlineEditMessage(instruction, context);

            // 4. Open AI view and send
            IWorkbenchPage page = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();
            AiAssistantView view = (AiAssistantView) page.showView(
                    AiAssistantView.VIEW_ID);
            view.sendMessage(message);

        } catch (Exception e) {
            System.err.println("InlineEditHandler: " + e.getMessage());
        }
        return null;
    }

    private String buildInlineEditMessage(String instruction, AdtContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("INLINE EDIT REQUEST for \"")
          .append(context.getObjectName() != null ? context.getObjectName() : "current object")
          .append("\":\n\n");

        sb.append("Instruction: ").append(instruction).append("\n\n");

        if (context.getSelectedText() != null && !context.getSelectedText().isEmpty()) {
            sb.append("Selected code to modify:\n```abap\n");
            sb.append(context.getSelectedText());
            sb.append("\n```\n\n");
        }

        if (context.getCursorLine() > 0) {
            sb.append("Cursor at line ").append(context.getCursorLine())
              .append(", column ").append(context.getCursorColumn()).append("\n\n");
        }

        sb.append("Please modify only the selected code according to the instruction. ");
        sb.append("Use sap_get_source to read the full source, then use ");
        sb.append("sap_write_and_check to apply your changes.");

        return sb.toString();
    }
}
