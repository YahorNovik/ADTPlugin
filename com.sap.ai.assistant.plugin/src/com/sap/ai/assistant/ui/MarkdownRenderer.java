package com.sap.ai.assistant.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;

/**
 * Applies lightweight Markdown styling to an SWT {@link StyledText} widget.
 * <p>
 * Supported syntax:
 * <ul>
 *   <li><b>Bold</b> &mdash; {@code **text**}</li>
 *   <li>Inline code &mdash; {@code `code`}</li>
 *   <li>Fenced code blocks &mdash; {@code ```...```}</li>
 *   <li>Bullet lists &mdash; lines starting with {@code "- "}</li>
 * </ul>
 * </p>
 */
public final class MarkdownRenderer {

    // Patterns applied in order.  Fenced blocks first so inner backticks
    // are not mis-detected as inline code.
    private static final Pattern CODE_BLOCK = Pattern.compile("```[^\\n]*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern BOLD       = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");

    private MarkdownRenderer() {
        // Utility class
    }

    /**
     * Parse the current text content of the widget and apply
     * {@link StyleRange}s for supported Markdown constructs.
     *
     * @param widget the StyledText to style (must not be disposed)
     */
    public static void applyMarkdownStyling(StyledText widget) {
        if (widget == null || widget.isDisposed()) {
            return;
        }

        String text = widget.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        Display display = widget.getDisplay();
        List<StyleRange> ranges = new ArrayList<>();

        // ---- Fenced code blocks ----
        applyCodeBlocks(text, ranges, display, widget);

        // ---- Bold (**text**) ----
        applyBold(text, ranges);

        // ---- Inline code (`code`) ----
        applyInlineCode(text, ranges, display, widget);

        // ---- Bullet indent (lines starting with "- ") ----
        applyBulletIndent(text, widget);

        // Merge and apply
        if (!ranges.isEmpty()) {
            // Sort by start offset to avoid SWT exceptions
            ranges.sort((a, b) -> Integer.compare(a.start, b.start));
            for (StyleRange range : ranges) {
                try {
                    widget.setStyleRange(range);
                } catch (Exception e) {
                    // Range may overlap or exceed bounds -- skip silently
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Pattern-specific styling
    // ------------------------------------------------------------------

    private static void applyCodeBlocks(String text, List<StyleRange> ranges,
                                         Display display, StyledText widget) {
        Font codeFont = getMonospaceFont(display, widget);
        Color bgColor = isDark(display)
                ? new Color(display, 35, 35, 40)
                : new Color(display, 240, 240, 240);

        Matcher m = CODE_BLOCK.matcher(text);
        while (m.find()) {
            StyleRange range = new StyleRange();
            range.start = m.start();
            range.length = m.end() - m.start();
            range.font = codeFont;
            range.background = bgColor;
            ranges.add(range);
        }
    }

    private static void applyBold(String text, List<StyleRange> ranges) {
        Matcher m = BOLD.matcher(text);
        while (m.find()) {
            StyleRange range = new StyleRange();
            range.start = m.start();
            range.length = m.end() - m.start();
            range.fontStyle = SWT.BOLD;
            ranges.add(range);
        }
    }

    private static void applyInlineCode(String text, List<StyleRange> ranges,
                                          Display display, StyledText widget) {
        Font codeFont = getMonospaceFont(display, widget);

        Matcher m = INLINE_CODE.matcher(text);
        while (m.find()) {
            // Skip matches that fall inside a fenced code block
            if (isInsideCodeBlock(text, m.start())) {
                continue;
            }
            StyleRange range = new StyleRange();
            range.start = m.start();
            range.length = m.end() - m.start();
            range.font = codeFont;
            ranges.add(range);
        }
    }

    private static void applyBulletIndent(String text, StyledText widget) {
        String[] lines = text.split("\\n", -1);
        int offset = 0;
        for (String line : lines) {
            if (line.startsWith("- ")) {
                try {
                    int lineIndex = widget.getLineAtOffset(offset);
                    widget.setLineIndent(lineIndex, 1, 20);
                } catch (Exception e) {
                    // Offset out of range -- skip
                }
            }
            offset += line.length() + 1; // +1 for the newline
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean isDark(Display display) {
        Color bg = display.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
        if (bg != null) {
            org.eclipse.swt.graphics.RGB rgb = bg.getRGB();
            return (rgb.red * 0.299 + rgb.green * 0.587 + rgb.blue * 0.114) < 128;
        }
        return false;
    }

    /**
     * Returns true if the given offset falls inside a fenced code block.
     */
    private static boolean isInsideCodeBlock(String text, int offset) {
        Matcher m = CODE_BLOCK.matcher(text);
        while (m.find()) {
            if (offset >= m.start() && offset < m.end()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a monospace font matching the widget's current font size.
     * Tries Menlo (macOS) then Consolas (Windows) then falls back to Courier.
     */
    private static Font getMonospaceFont(Display display, StyledText widget) {
        int size = 11;
        try {
            FontData[] fd = widget.getFont().getFontData();
            if (fd.length > 0) {
                size = fd[0].getHeight();
            }
        } catch (Exception e) {
            // Use default size
        }

        // Try platform-appropriate monospace fonts
        String[] candidates = { "Menlo", "Consolas", "Courier New", "Courier" };
        for (String name : candidates) {
            try {
                Font font = new Font(display, name, size, SWT.NORMAL);
                // Verify the font was actually created with the right name
                if (font.getFontData().length > 0) {
                    return font;
                }
            } catch (Exception e) {
                // Font not available -- try next
            }
        }
        // Last resort -- return widget font
        return widget.getFont();
    }
}
