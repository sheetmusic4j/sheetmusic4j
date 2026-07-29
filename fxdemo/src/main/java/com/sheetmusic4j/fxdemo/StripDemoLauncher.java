package com.sheetmusic4j.fxdemo;

/**
 * Plain launcher for {@link StripDemoApp} that does not extend
 * {@link javafx.application.Application}.
 *
 * <p>When JavaFX is on the classpath (rather than the module path), launching a
 * class that extends {@code Application} directly fails with "JavaFX runtime
 * components are missing". Delegating through this launcher avoids that check and
 * lets the strip demo run from a standard Java run/launch configuration.
 */
public final class StripDemoLauncher {

    private StripDemoLauncher() {
    }

    /**
     * Starts the JavaFX strip demo through an indirection classpath launchers can use.
     *
     * @param args command-line arguments forwarded to {@link StripDemoApp}
     */
    public static void main(String[] args) {
        StripDemoApp.main(args);
    }
}
