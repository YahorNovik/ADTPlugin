package com.sap.ai.assistant.handlers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import com.sap.ai.assistant.ui.AiAssistantView;

/**
 * A single quick-fix resolution that sends the marker's error to the
 * SAP AI Assistant for automated fixing.
 */
public class AiQuickFixResolution implements IMarkerResolution2 {

    private final IMarker marker;

    public AiQuickFixResolution(IMarker marker) {
        this.marker = marker;
    }

    @Override
    public String getLabel() {
        return "Fix with AI Assistant";
    }

    @Override
    public String getDescription() {
        return "Send this error to the SAP AI Assistant for automatic fixing.";
    }

    @Override
    public Image getImage() {
        return null;
    }

    @Override
    public void run(IMarker marker) {
        try {
            String message = marker.getAttribute(IMarker.MESSAGE, "Unknown error");
            int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
            int severity = marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);

            String prefix = severity == IMarker.SEVERITY_ERROR ? "ERROR" : "WARNING";
            String errorDesc = prefix
                    + (line > 0 ? " [line " + line + "]" : "")
                    + ": " + message;

            String objectName = marker.getResource() != null
                    ? marker.getResource().getName() : "unknown";

            StringBuilder prompt = new StringBuilder();
            prompt.append("FIX ERROR REQUEST for \"")
                  .append(objectName).append("\":\n\n");
            prompt.append("Error: ").append(errorDesc).append("\n\n");
            prompt.append("Please:\n");
            prompt.append("1. Read the source using sap_get_source\n");
            prompt.append("2. Fix this specific error\n");
            prompt.append("3. Write the fix using sap_write_and_check\n");
            prompt.append("4. Verify with sap_syntax_check\n");

            IWorkbenchPage page = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();
            AiAssistantView view = (AiAssistantView) page.showView(
                    AiAssistantView.VIEW_ID);
            view.sendMessage(prompt.toString());

        } catch (Exception e) {
            System.err.println("AiQuickFixResolution: " + e.getMessage());
        }
    }
}
