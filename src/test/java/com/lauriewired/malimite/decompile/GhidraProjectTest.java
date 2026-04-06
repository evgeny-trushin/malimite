package com.lauriewired.malimite.decompile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;

import junit.framework.TestCase;

public class GhidraProjectTest extends TestCase {

    public void testBuildAnalyzeHeadlessCommandUsesSupportedGhidra12Options() {
        List<String> command = GhidraProject.buildAnalyzeHeadlessCommand(
            "/opt/ghidra/support/analyzeHeadless",
            "/tmp/project",
            "carsick_malimite",
            "/tmp/project/carsick",
            "/tmp/scripts",
            8765,
            "Foundation,UIKit"
        );

        int postScriptIndex = command.indexOf("-postScript");
        int scriptNameIndex = postScriptIndex + 1;
        int portIndex = postScriptIndex + 2;
        int librariesIndex = postScriptIndex + 3;

        assertTrue(postScriptIndex > 0);
        assertEquals("DumpClassData.java", command.get(scriptNameIndex));
        assertEquals("8765", command.get(portIndex));
        assertEquals("Foundation,UIKit", command.get(librariesIndex));
        assertEquals(command.size(), postScriptIndex + 4);
        assertEquals(-1, command.indexOf("-enableAnalyzer"));
        assertEquals(-1, command.indexOf("-disableAnalyzer"));
        assertEquals(-1, command.indexOf("-skipAnalysisPrompt"));
        assertTrue(command.indexOf("-deleteProject") < postScriptIndex);
    }

    public void testReadDelimitedBlockThrowsOnUnexpectedEof() {
        BufferedReader reader = new BufferedReader(new StringReader("line one\nline two\n"));

        IOException error = null;
        try {
            GhidraProject.readDelimitedBlock(reader, "END_CLASS_DATA", "class data");
        } catch (IOException e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error.getMessage().contains("class data"));
        assertTrue(error.getMessage().contains("END_CLASS_DATA"));
    }

    public void testExtractClassFunctionsUsesClassPayload() {
        JSONArray classData = new JSONArray(
            "[{\"ClassName\":\"WidgetController\",\"Functions\":[\"viewDidLoad\",\"tap:\"]}]"
        );

        Map<String, JSONArray> classesToFunctions = GhidraProject.extractClassFunctions(classData);

        assertEquals(1, classesToFunctions.size());
        assertTrue(classesToFunctions.containsKey("WidgetController"));
        assertEquals(2, classesToFunctions.get("WidgetController").length());
        assertEquals("viewDidLoad", classesToFunctions.get("WidgetController").getString(0));
        assertEquals("tap:", classesToFunctions.get("WidgetController").getString(1));
    }

    public void testOpenBridgeServerSocketFallsBackWhenPreferredRangeIsBusy() throws Exception {
        ArrayList<Integer> requestedPorts = new ArrayList<>();
        ServerSocket bridgeSocket = GhidraProject.openBridgeServerSocket(30000, 3, port -> {
            requestedPorts.add(port);
            if (port == 0) {
                return new FakeServerSocket(45123);
            }
            throw new IOException("busy");
        });

        assertNotNull(bridgeSocket);
        assertEquals(45123, bridgeSocket.getLocalPort());
        assertEquals(Arrays.asList(30000, 30001, 30002, 0), requestedPorts);
    }

    private static final class FakeServerSocket extends ServerSocket {
        private final int localPort;

        private FakeServerSocket(int localPort) throws IOException {
            super();
            this.localPort = localPort;
        }

        @Override
        public int getLocalPort() {
            return localPort;
        }

        @Override
        public boolean isBound() {
            return true;
        }
    }
}
