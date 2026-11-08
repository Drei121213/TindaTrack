package tindatrack;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Entry point for the TindaTrack desktop application.
 */
public class TindaTrackApplication extends Application {

    /**
     * Starts the JavaFX runtime and calls start on the JavaFX application thread.
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Checks that JavaFX starts. The first window will be added in Task 2.
     */
    @Override
    public void start(Stage primaryStage) {
        System.out.println("TindaTrack setup successful: JavaFX is running.");
        Platform.exit();
    }
}
