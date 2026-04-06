package com.lauriewired.malimite.utils;

import com.lauriewired.malimite.database.SQLiteDBHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProjectExporter {
    private static final Logger LOGGER = Logger.getLogger(ProjectExporter.class.getName());

    public static int exportProject(SQLiteDBHandler dbHandler, String executableName, File outputDir) throws IOException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create export directory: " + outputDir);
        }

        int totalFiles = 0;

        // Export decompiled functions grouped by class
        Map<String, Map<String, String>> classMap = dbHandler.getAllDecompiledFunctions(executableName);
        LOGGER.info("Exporting " + classMap.size() + " classes");

        File srcDir = new File(outputDir, "src");
        srcDir.mkdirs();

        for (Map.Entry<String, Map<String, String>> classEntry : classMap.entrySet()) {
            String className = classEntry.getKey();
            Map<String, String> functions = classEntry.getValue();

            if ("Libraries".equals(className)) {
                continue; // Skip library stubs
            }

            String safeClassName = sanitizeFileName(className);
            File classFile = new File(srcDir, safeClassName + ".c");

            try (PrintWriter writer = new PrintWriter(new FileWriter(classFile))) {
                writer.println("// Class: " + className);
                writer.println("// Functions: " + functions.size());
                writer.println("// Decompiled by Malimite");
                writer.println();

                for (Map.Entry<String, String> funcEntry : functions.entrySet()) {
                    String funcName = funcEntry.getKey();
                    String code = funcEntry.getValue();

                    if (code != null && !code.trim().isEmpty()) {
                        writer.println("// ---- " + funcName + " ----");
                        writer.println(code.trim());
                        writer.println();
                    }
                }
            }
            totalFiles++;
        }

        // Export library function list
        if (classMap.containsKey("Libraries")) {
            Map<String, String> libFunctions = classMap.get("Libraries");
            File libFile = new File(srcDir, "_Libraries.txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(libFile))) {
                writer.println("// Library functions (" + libFunctions.size() + ")");
                writer.println("// These are external library calls referenced by the binary");
                writer.println();
                for (String funcName : libFunctions.keySet()) {
                    writer.println(funcName);
                }
            }
            totalFiles++;
        }

        // Export strings
        List<String[]> strings = dbHandler.getAllStrings(executableName);
        if (!strings.isEmpty()) {
            File stringsFile = new File(outputDir, "strings.txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(stringsFile))) {
                writer.println("// Extracted strings (" + strings.size() + ")");
                writer.println("// Format: address | segment | label | value");
                writer.println();
                for (String[] s : strings) {
                    writer.printf("%-12s | %-20s | %-30s | %s%n", s[0], s[2], s[3], s[1]);
                }
            }
            totalFiles++;
        }

        // Export summary
        File summaryFile = new File(outputDir, "README.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryFile))) {
            writer.println("Malimite Decompilation Export");
            writer.println("============================");
            writer.println("Executable: " + executableName);
            writer.println("Classes: " + (classMap.size() - (classMap.containsKey("Libraries") ? 1 : 0)));
            writer.println("Functions: " + classMap.values().stream().mapToInt(Map::size).sum());
            writer.println("Strings: " + strings.size());
            writer.println();
            writer.println("Structure:");
            writer.println("  src/           - Decompiled source files (one per class)");
            writer.println("  strings.txt    - Extracted string constants");
            writer.println("  README.txt     - This file");
        }
        totalFiles++;

        LOGGER.info("Export complete: " + totalFiles + " files written to " + outputDir);
        return totalFiles;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_.$<>\\-]", "_")
                   .replaceAll("[<>]", "_");
    }
}
