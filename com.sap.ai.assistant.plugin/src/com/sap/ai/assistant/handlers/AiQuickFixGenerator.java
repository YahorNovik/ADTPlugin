package com.sap.ai.assistant.handlers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;

/**
 * Generates "Fix with AI Assistant" quick-fix proposals for problem markers
 * (errors and warnings) in the Eclipse editor.
 * <p>
 * Registered via the {@code org.eclipse.ui.ide.markerResolution} extension
 * point in {@code plugin.xml}.
 * </p>
 */
public class AiQuickFixGenerator implements IMarkerResolutionGenerator2 {

    @Override
    public boolean hasResolutions(IMarker marker) {
        try {
            int severity = marker.getAttribute(IMarker.SEVERITY, -1);
            return severity == IMarker.SEVERITY_ERROR
                    || severity == IMarker.SEVERITY_WARNING;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public IMarkerResolution[] getResolutions(IMarker marker) {
        return new IMarkerResolution[] {
            new AiQuickFixResolution(marker)
        };
    }
}
