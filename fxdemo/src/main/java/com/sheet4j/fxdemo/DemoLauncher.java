package com.sheet4j.fxdemo;

/**
 * Plain launcher that does not extend {@link javafx.application.Application}.
 *
 * <p>When JavaFX is on the classpath (rather than the module path), launching a
 * class that extends {@code Application} directly fails with "JavaFX runtime
 * components are missing". Delegating through this launcher avoids that check and
 * lets the demo run from a standard Java run/launch configuration.
 */
public final class DemoLauncher {

    private DemoLauncher() {
    }

    public static void main(String[] args) {
        SheetDemoApp.main(args);
    }
}
