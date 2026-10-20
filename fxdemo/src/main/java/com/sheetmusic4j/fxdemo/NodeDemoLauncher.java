package com.sheetmusic4j.fxdemo;

/**
 * Plain launcher for {@link NodeDemoApp} that does not extend
 * {@link javafx.application.Application}, so it runs from a classpath-based
 * launch configuration without the "JavaFX runtime components are missing" error.
 */
public final class NodeDemoLauncher {

    private NodeDemoLauncher() {
    }

    /**
     * Starts the JavaFX node-view demo.
     *
     * @param args command-line arguments forwarded to {@link NodeDemoApp}
     */
    public static void main(String[] args) {
        NodeDemoApp.main(args);
    }
}
