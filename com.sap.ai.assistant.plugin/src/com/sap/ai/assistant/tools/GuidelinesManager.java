package com.sap.ai.assistant.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages persistent guideline files for ABAP object types.
 * <p>
 * Files are stored in {@code ~/.sap-ai-assistant/guidelines/} as Markdown files.
 * Each object type maps to a specific filename (e.g. CLAS → class.md,
 * PROG → report.md). The agent reads these before creating/modifying objects
 * and can write notes for future reference.
 * </p>
 */
public final class GuidelinesManager {

    private GuidelinesManager() {}

    /** Base directory for all guideline files. */
    private static final Path GUIDELINES_DIR =
            Path.of(System.getProperty("user.home"), ".sap-ai-assistant", "guidelines");

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Maps ABAP object type keywords to guideline filenames.
     */
    private static final Map<String, String> TYPE_TO_FILE = new LinkedHashMap<>();
    static {
        TYPE_TO_FILE.put("CLAS",      "class.md");
        TYPE_TO_FILE.put("CLASS",     "class.md");
        TYPE_TO_FILE.put("INTF",      "interface.md");
        TYPE_TO_FILE.put("INTERFACE", "interface.md");
        TYPE_TO_FILE.put("PROG",      "report.md");
        TYPE_TO_FILE.put("PROGRAM",   "report.md");
        TYPE_TO_FILE.put("FUGR",      "functionmodule.md");
        TYPE_TO_FILE.put("TABL",      "table.md");
        TYPE_TO_FILE.put("TABLE",     "table.md");
        TYPE_TO_FILE.put("STRU",      "structure.md");
        TYPE_TO_FILE.put("STRUCTURE", "structure.md");
        TYPE_TO_FILE.put("DDLS",      "cdsview.md");
        TYPE_TO_FILE.put("CDS",       "cdsview.md");
        TYPE_TO_FILE.put("DTEL",      "dataelement.md");
        TYPE_TO_FILE.put("DATAELEMENT", "dataelement.md");
        TYPE_TO_FILE.put("DOMA",      "domain.md");
        TYPE_TO_FILE.put("DOMAIN",    "domain.md");
        TYPE_TO_FILE.put("SRVD",      "servicedefinition.md");
        TYPE_TO_FILE.put("DDLX",      "metadataextension.md");
        TYPE_TO_FILE.put("BDEF",      "behaviordefinition.md");
    }

    /**
     * Resolves a filename from objectType and/or fileName.
     * Prefers fileName if provided; otherwise maps objectType.
     *
     * @param objectType the ABAP object type (e.g. "CLAS")
     * @param fileName   a direct filename (e.g. "class.md", "custom.md")
     * @return the resolved filename, or {@code null} if neither is valid
     */
    public static String resolveFileName(String objectType, String fileName) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            String name = fileName.trim();
            if (!name.endsWith(".md")) {
                name = name + ".md";
            }
            return name;
        }
        if (objectType != null && !objectType.trim().isEmpty()) {
            return TYPE_TO_FILE.get(objectType.toUpperCase().trim());
        }
        return null;
    }

    /**
     * Reads the content of a guideline file.
     *
     * @param fileName the filename (e.g. "class.md")
     * @return the file content, or {@code null} if the file doesn't exist
     * @throws IOException if reading fails
     */
    public static String readFile(String fileName) throws IOException {
        Path path = GUIDELINES_DIR.resolve(fileName);
        if (!Files.exists(path)) {
            return null;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Appends content to a guideline file with a timestamp separator.
     * Creates the directory and file if they don't exist.
     *
     * @param fileName the filename
     * @param content  the content to append
     * @throws IOException if writing fails
     */
    public static void appendToFile(String fileName, String content) throws IOException {
        ensureDirectory();
        Path path = GUIDELINES_DIR.resolve(fileName);

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String entry = "\n---\n_Added: " + timestamp + "_\n\n" + content.trim() + "\n";

        Files.writeString(path, entry, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Replaces the entire content of a guideline file.
     * Creates the directory and file if they don't exist.
     *
     * @param fileName the filename
     * @param content  the new content
     * @throws IOException if writing fails
     */
    public static void replaceFile(String fileName, String content) throws IOException {
        ensureDirectory();
        Path path = GUIDELINES_DIR.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Lists all existing guideline files with their sizes.
     *
     * @return a list of "filename (size)" entries, or empty list
     * @throws IOException if listing fails
     */
    public static List<String> listFiles() throws IOException {
        List<String> result = new ArrayList<>();
        if (!Files.isDirectory(GUIDELINES_DIR)) {
            return result;
        }
        try (Stream<Path> files = Files.list(GUIDELINES_DIR)) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .sorted()
                 .forEach(p -> {
                     try {
                         long size = Files.size(p);
                         result.add(p.getFileName().toString() + " (" + size + " bytes)");
                     } catch (IOException e) {
                         result.add(p.getFileName().toString() + " (unknown size)");
                     }
                 });
        }
        return result;
    }

    /**
     * Returns the path to the guidelines directory.
     */
    public static Path getGuidelinesDir() {
        return GUIDELINES_DIR;
    }

    /**
     * Ensures the guidelines directory exists.
     */
    private static void ensureDirectory() throws IOException {
        if (!Files.isDirectory(GUIDELINES_DIR)) {
            Files.createDirectories(GUIDELINES_DIR);
        }
    }
}
