package tindatrack;

import javafx.application.Application;
import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
     * Loads the welcome layout and displays the application's first window.
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxmlUrl = getClass().getResource("/tindatrack/fxml/welcome.fxml");
        URL cssUrl = getClass().getResource("/tindatrack/css/welcome.css");

        if (fxmlUrl == null || cssUrl == null) {
            throw new IOException("The welcome FXML or CSS resource could not be found.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        Scene scene = new Scene(root, 720, 460);
        scene.getStylesheets().add(cssUrl.toExternalForm());

        primaryStage.setTitle("TindaTrack");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.show();
    }
}
