package com.lauriewired.malimite.decompile;

import com.lauriewired.malimite.files.Macho;
import com.lauriewired.malimite.configuration.Config;
import com.lauriewired.malimite.configuration.LibraryDefinitions;
import com.lauriewired.malimite.database.SQLiteDBHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.*;
import java.sql.SQLException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class GhidraProject {
    @FunctionalInterface
    interface BridgeServerSocketFactory {
        ServerSocket create(int port) throws IOException;
    }

    private static final Logger LOGGER = Logger.getLogger(GhidraProject.class.getName());
    private String ghidraProjectName;
    private Config config;
    private String scriptPath;
    private SQLiteDBHandler dbHandler;
    private static final int BASE_PORT = 18765;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final int HEARTBEAT_TIMEOUT_MS = 600_000;  // 10 min — Ghidra must finish full analysis before post-script sends heartbeat
    private static final int DATA_CONNECTION_TIMEOUT_MS = 600_000; // 10 min — decompiling all functions can take several minutes
    private Consumer<String> consoleOutputCallback;

    public GhidraProject(String infoPlistBundleExecutable, String executableFilePath, Config config, SQLiteDBHandler dbHandler, Consumer<String> consoleOutputCallback) {
        this.ghidraProjectName = infoPlistBundleExecutable + "_malimite";
        this.config = config;
        this.dbHandler = dbHandler;
        this.consoleOutputCallback = consoleOutputCallback;
        // Set script path: try JAR location first, then current directory
        String jarDir = getJarDirectory();
        Path candidate = Paths.get(jarDir, "DecompilerBridge", "ghidra");
        if (Files.isDirectory(candidate)) {
            this.scriptPath = candidate.toString();
        } else {
            // Fall back to current working directory
            String currentDir = System.getProperty("user.dir");
            this.scriptPath = Paths.get(currentDir, "DecompilerBridge", "ghidra").toString();
        }

        LOGGER.info("Initializing GhidraProject with executable: " + infoPlistBundleExecutable);
        LOGGER.info("Script path: " + scriptPath);
    }

    public void decompileMacho(String executableFilePath, String projectDirectoryPath, Macho targetMacho, boolean dynamicFile) {
        LOGGER.info("Starting Ghidra decompilation for: " + executableFilePath);

        ServerSocket serverSocket;
        try {
            serverSocket = openBridgeServerSocket(BASE_PORT, MAX_PORT_ATTEMPTS);
        } catch (IOException e) {
            throw new RuntimeException("Unable to bind bridge server socket", e);
        }
        int port = serverSocket.getLocalPort();

        try (ServerSocket finalServerSocket = serverSocket) {  // Ensure socket gets closed
            String analyzeHeadless = getAnalyzeHeadlessPath();
            
            // Get active libraries and join them with commas
            List<String> activeLibraries = LibraryDefinitions.getActiveLibraries(config);
            String librariesArg = String.join(",", activeLibraries);

            ProcessBuilder builder = new ProcessBuilder(buildAnalyzeHeadlessCommand(
                analyzeHeadless,
                projectDirectoryPath,
                this.ghidraProjectName,
                executableFilePath,
                scriptPath,
                port,
                librariesArg
            ));

            builder.redirectErrorStream(true);
            Process process = builder.start();

            Thread outputThread = new Thread(() -> {
                try (BufferedReader ghidraOutput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = ghidraOutput.readLine()) != null) {
                        if (consoleOutputCallback != null) {
                            consoleOutputCallback.accept("Ghidra: " + line);
                        }
                        System.out.println("Ghidra Output: " + line);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error reading Ghidra output", e);
                }
            }, "ghidra-output-" + port);
            outputThread.setDaemon(true);
            outputThread.start();

            logBridgeMessage("Starting Ghidra headless analyzer with command: " + String.join(" ", builder.command()));
            logBridgeMessage("Waiting for Ghidra script heartbeat on port " + port);

            try (Socket heartbeatSocket = acceptSocket(finalServerSocket, HEARTBEAT_TIMEOUT_MS, "heartbeat");
                 BufferedReader heartbeatReader = new BufferedReader(new InputStreamReader(heartbeatSocket.getInputStream()))) {
                heartbeatSocket.setSoTimeout(HEARTBEAT_TIMEOUT_MS);
                logBridgeMessage("Connection established with Ghidra script");

                String heartbeat = readRequiredLine(heartbeatReader, "heartbeat");
                if (!"HEARTBEAT".equals(heartbeat)) {
                    throw new IOException("Unexpected heartbeat message from Ghidra script: " + heartbeat);
                }
                logBridgeMessage("Received heartbeat from Ghidra script");
            }

            logBridgeMessage("Waiting for Ghidra data connection on port " + port);

            try (Socket dataSocket = acceptSocket(finalServerSocket, DATA_CONNECTION_TIMEOUT_MS, "data");
                 BufferedReader in = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()))) {
                dataSocket.setSoTimeout(0);

                String connectionConfirmation = readRequiredLine(in, "connection confirmation");
                if (!"CONNECTED".equals(connectionConfirmation)) {
                    throw new IOException("Unexpected connection confirmation from Ghidra script: " + connectionConfirmation);
                }
                logBridgeMessage("Ghidra script confirmed connection, beginning analysis");

                String classDataPayload = readDelimitedBlock(in, "END_CLASS_DATA", "class data");
                String machoDataPayload = readDelimitedBlock(in, "END_MACHO_DATA", "Mach-O data");
                String functionDataPayload = readDelimitedBlock(in, "END_DATA", "function data");
                String stringDataPayload = readDelimitedBlock(in, "END_STRING_DATA", "string data");

                JSONArray classData = new JSONArray(classDataPayload);
                JSONObject machoData = new JSONObject(machoDataPayload);
                JSONArray functionData = new JSONArray(functionDataPayload);
                JSONArray stringData = new JSONArray(stringDataPayload);

                logBridgeMessage(
                    "Processing " + classData.length() + " classes, "
                        + functionData.length() + " functions, "
                        + stringData.length() + " strings from Ghidra analysis"
                );
                LOGGER.info("Received Mach-O metadata for " + machoData.length() + " segments");
                if (classData.length() == 0 && functionData.length() == 0 && stringData.length() == 0) {
                    logBridgeMessage("Ghidra bridge received empty payloads. Check analyzer configuration and script output.");
                }

                Map<String, JSONArray> classToFunctions = extractClassFunctions(classData);
                Map<String, String> classNameMapping = new HashMap<>();

                ArrayList<SQLiteDBHandler.DecompilationResult> decompilationResults = new ArrayList<>();
                final int totalFunctions = functionData.length();
                final int BATCH_SIZE = 500;

                logBridgeMessage("Processing " + totalFunctions + " functions...");

                for (int i = 0; i < totalFunctions; i++) {
                    JSONObject functionObj = functionData.getJSONObject(i);
                    String functionName = functionObj.getString("FunctionName");
                    String className = functionObj.getString("ClassName");
                    String decompiledCode = functionObj.getString("DecompiledCode");

                    if (!config.isMac() && targetMacho.isSwift() && functionName.startsWith("_$s")) {
                        DemangleSwift.DemangledName demangledName = DemangleSwift.demangleSwiftName(functionName);
                        if (demangledName != null) {
                            className = demangledName.className;
                            functionName = demangledName.fullMethodName;
                        }
                    }

                    if (className == null || className.trim().isEmpty()) {
                        className = "Global";
                    }

                    final String finalClassName = className;
                    boolean isLibrary = activeLibraries.stream()
                            .anyMatch(library -> finalClassName.startsWith(library));

                    if (!isLibrary) {
                        decompiledCode = decompiledCode.replaceAll("/\\*.*\\*/", "");

                        if (!decompiledCode.trim().startsWith("// Class:") && !decompiledCode.trim().startsWith("// Function:")) {
                            decompiledCode = "// Class: " + className + "\n// Function: " + functionName + "\n\n" + decompiledCode.trim();
                        }

                        decompilationResults.add(new SQLiteDBHandler.DecompilationResult(
                            functionName, className, decompiledCode, targetMacho.getMachoExecutableName()));

                        JSONArray functions = classToFunctions.computeIfAbsent(className, key -> new JSONArray());
                        appendIfMissing(functions, functionName);
                    } else {
                        String libraryFunctionName = className + "::" + functionName;
                        classNameMapping.put(className, "Libraries");

                        decompilationResults.add(new SQLiteDBHandler.DecompilationResult(
                            libraryFunctionName, "Libraries", targetMacho.getMachoExecutableName(), targetMacho.getMachoExecutableName()));

                        JSONArray functions = classToFunctions.computeIfAbsent("Libraries", key -> new JSONArray());
                        appendIfMissing(functions, libraryFunctionName);
                    }

                    // Log progress every 100 functions and flush to DB every BATCH_SIZE
                    int done = i + 1;
                    if (done % 100 == 0 || done == totalFunctions) {
                        String msg = "[" + done + "/" + totalFunctions + "] functions processed";
                        LOGGER.info(msg);
                        if (consoleOutputCallback != null) consoleOutputCallback.accept(msg);
                    }
                    if (done % BATCH_SIZE == 0) {
                        dbHandler.insertFunctionDecompilations(decompilationResults);
                        decompilationResults.clear();
                    }
                }

                // Insert remaining
                if (!decompilationResults.isEmpty()) {
                    dbHandler.insertFunctionDecompilations(decompilationResults);
                    decompilationResults.clear();
                }
                logBridgeMessage("Function processing complete: " + totalFunctions + " functions");

                for (String originalLibraryClass : classNameMapping.keySet()) {
                    classToFunctions.remove(originalLibraryClass);
                }

                for (Map.Entry<String, JSONArray> entry : classToFunctions.entrySet()) {
                    String className = entry.getKey();
                    JSONArray functions = entry.getValue();
                    LOGGER.info("Inserting class: " + className + " with " + functions.length() + " functions");
                    dbHandler.insertClass(className, functions.toString(), targetMacho.getMachoExecutableName());
                }
                commitPendingChanges("class metadata");

                LOGGER.info("Processing " + stringData.length() + " strings from Ghidra analysis");

                for (int i = 0; i < stringData.length(); i++) {
                    JSONObject stringObj = stringData.getJSONObject(i);
                    String address = stringObj.getString("address");
                    String value = stringObj.getString("value");
                    String segment = stringObj.getString("segment");
                    String label = stringObj.getString("label");
                    LOGGER.info("Inserting string: " + value + " at address: " + address);
                    dbHandler.insertMachoString(address, value, segment, label, targetMacho.getMachoExecutableName());
                }
                commitPendingChanges("Ghidra bridge import");

                logBridgeMessage("Finished processing all data");
            }

            int exitCode = process.waitFor();
            outputThread.join(5_000);
            if (exitCode != 0) {
                throw new IOException("Ghidra headless analysis exited with code " + exitCode);
            }
            logBridgeMessage("Ghidra analysis completed successfully");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during Ghidra decompilation", e);
            throw new RuntimeException("Ghidra decompilation failed: " + e.getMessage(), e);
        }
    }

    static List<String> buildAnalyzeHeadlessCommand(
            String analyzeHeadless,
            String projectDirectoryPath,
            String ghidraProjectName,
            String executableFilePath,
            String scriptPath,
            int port,
            String librariesArg) {
        ArrayList<String> command = new ArrayList<>();
        command.add(analyzeHeadless);
        command.add(projectDirectoryPath);
        command.add(ghidraProjectName);
        command.add("-import");
        command.add(executableFilePath);
        command.add("-scriptPath");
        command.add(scriptPath);
        command.add("-deleteProject");
        command.add("-postScript");
        command.add("DumpClassData.java");
        command.add(String.valueOf(port));
        command.add(librariesArg);
        return command;
    }

    static ServerSocket openBridgeServerSocket(int basePort, int maxPortAttempts) throws IOException {
        return openBridgeServerSocket(basePort, maxPortAttempts, ServerSocket::new);
    }

    static ServerSocket openBridgeServerSocket(
            int basePort,
            int maxPortAttempts,
            BridgeServerSocketFactory socketFactory) throws IOException {
        int port = basePort;
        int attempts = 0;

        while (attempts < maxPortAttempts) {
            try {
                ServerSocket socket = socketFactory.create(port);
                LOGGER.info("Successfully bound to port " + socket.getLocalPort());
                return socket;
            } catch (IOException e) {
                LOGGER.warning("Port " + port + " is in use, trying next port");
                port++;
                attempts++;
            }
        }

        ServerSocket socket = socketFactory.create(0);
        LOGGER.info("Preferred bridge port range exhausted, using ephemeral port " + socket.getLocalPort());
        return socket;
    }

    static String readDelimitedBlock(BufferedReader in, String endMarker, String sectionName) throws IOException {
        StringBuilder dataBuilder = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (endMarker.equals(line)) {
                return dataBuilder.toString();
            }
            dataBuilder.append(line).append("\n");
        }
        throw new IOException("Unexpected end of stream while reading " + sectionName + ". Missing marker " + endMarker);
    }

    static Map<String, JSONArray> extractClassFunctions(JSONArray classData) {
        Map<String, JSONArray> classToFunctions = new HashMap<>();
        for (int i = 0; i < classData.length(); i++) {
            JSONObject classObject = classData.getJSONObject(i);
            String className = classObject.optString("ClassName", "Global");
            if (className == null || className.trim().isEmpty()) {
                className = "Global";
            }

            JSONArray functions = classObject.optJSONArray("Functions");
            JSONArray normalizedFunctions = new JSONArray();
            if (functions != null) {
                for (int functionIndex = 0; functionIndex < functions.length(); functionIndex++) {
                    appendIfMissing(normalizedFunctions, functions.getString(functionIndex));
                }
            }
            classToFunctions.put(className, normalizedFunctions);
        }
        return classToFunctions;
    }

    private static void appendIfMissing(JSONArray functions, String functionName) {
        for (int i = 0; i < functions.length(); i++) {
            if (functionName.equals(functions.getString(i))) {
                return;
            }
        }
        functions.put(functionName);
    }

    private static String readRequiredLine(BufferedReader in, String description) throws IOException {
        String line = in.readLine();
        if (line == null) {
            throw new IOException("Unexpected end of stream while reading " + description);
        }
        return line;
    }

    private Socket acceptSocket(ServerSocket serverSocket, int timeoutMs, String phaseName) throws IOException {
        serverSocket.setSoTimeout(timeoutMs);
        try {
            return serverSocket.accept();
        } catch (SocketTimeoutException e) {
            throw new IOException(
                "Timed out waiting for Ghidra script " + phaseName + " connection after " + (timeoutMs / 1000) + " seconds",
                e
            );
        }
    }

    private void logBridgeMessage(String message) {
        LOGGER.info(message);
        if (consoleOutputCallback != null) {
            consoleOutputCallback.accept(message);
        }
    }

    private void commitPendingChanges(String context) throws SQLException {
        dbHandler.GetTransaction().commit();
        LOGGER.info("Committed database transaction after " + context);
    }

    private static String getJarDirectory() {
        try {
            String jarPath = GhidraProject.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            return new File(jarPath).getParent();
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    private String getAnalyzeHeadlessPath() {
        String analyzeHeadless = Paths.get(config.getGhidraPath(), "support", "analyzeHeadless").toString();
        if (config.isWindows()) {
            analyzeHeadless += ".bat";
        }
        LOGGER.info("Using analyzeHeadless path: " + analyzeHeadless);
        return analyzeHeadless;
    }

}
