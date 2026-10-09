package com.sheetmusic4j.fxdemo;

/**
 * Plain launcher for {@link FlyOverDemoApp} that does not extend
 * {@link javafx.application.Application}.
 *
 * <p>When JavaFX is on the classpath (rather than the module path), launching a
 * class that extends {@code Application} directly fails with "JavaFX runtime
 * components are missing". Delegating through this launcher avoids that check and
 * lets the fly-over demo run from a standard Java run/launch configuration.
 */
public final class FlyOverDemoLauncher {

    private FlyOverDemoLauncher() {
    }

    /**
     * Starts the JavaFX fly-over demo through an indirection classpath launchers can use.
     *
     * @param args command-line arguments forwarded to {@link FlyOverDemoApp}
     */
    public static void main(String[] args) {
        FlyOverDemoApp.main(args);
    }
}
