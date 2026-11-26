package eu.hansolo.hotspotter.analyzer.fx;

import javafx.application.Application;


/** Plain main class (does not extend Application) so the app also starts from the class path without module-info. */
public final class Launcher {

    private Launcher() {}


    public static void main(final String[] args) { Application.launch(App.class, args); }
}
