package com.lauriewired.malimite.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.tree.DefaultMutableTreeNode;

import junit.framework.TestCase;

public class AnalysisWindowTest extends TestCase {

    public void testFindPreferredAnalysisNodePrefersAppFunctionOverLibraries() {
        DefaultMutableTreeNode classesRootNode = new DefaultMutableTreeNode("Classes");
        DefaultMutableTreeNode librariesNode = new DefaultMutableTreeNode("Libraries");
        librariesNode.add(new DefaultMutableTreeNode("libFunc"));
        classesRootNode.add(librariesNode);

        DefaultMutableTreeNode appClassNode = new DefaultMutableTreeNode("AppClass");
        DefaultMutableTreeNode appFunctionNode = new DefaultMutableTreeNode("launch");
        appClassNode.add(appFunctionNode);
        classesRootNode.add(appClassNode);

        DefaultMutableTreeNode fallbackFileNode = new DefaultMutableTreeNode("test.app");

        DefaultMutableTreeNode preferredNode = AnalysisWindow.findPreferredAnalysisNode(
            classesRootNode,
            fallbackFileNode
        );

        assertSame(appFunctionNode, preferredNode);
    }

    public void testShouldDisplayBinaryPlaceholderForStandaloneMachO() throws Exception {
        Path machoPath = Files.createTempFile("malimite-macho", ".bin");
        Files.write(machoPath, new byte[] {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCF});

        File machoFile = machoPath.toFile();

        try {
            assertTrue(AnalysisWindow.shouldDisplayBinaryPlaceholder(
                machoFile,
                machoFile.getAbsolutePath()
            ));
        } finally {
            Files.deleteIfExists(machoPath);
        }
    }

    public void testShouldNotDisplayBinaryPlaceholderForPlainTextFile() throws Exception {
        Path textPath = Files.createTempFile("malimite-text", ".txt");
        Files.writeString(textPath, "plain text");

        File textFile = textPath.toFile();

        try {
            assertFalse(AnalysisWindow.shouldDisplayBinaryPlaceholder(
                textFile,
                textFile.getAbsolutePath()
            ));
        } finally {
            Files.deleteIfExists(textPath);
        }
    }
}
