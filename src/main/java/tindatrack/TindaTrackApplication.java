package tindatrack;

import java.io.IOException;
import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import tindatrack.authentication.LoginController;
import tindatrack.authentication.OwnerSession;
import tindatrack.core.OwnerHomeController;
import tindatrack.core.SessionState;
import tindatrack.guest.GuestController;
import tindatrack.guest.GuestService;
import tindatrack.guest.GuestSession;

/**
 * Entry point for the TindaTrack desktop application.
 */
public class TindaTrackApplication extends Application {

    private final SessionState sessions = new SessionState();

    /**
     * Starts the JavaFX runtime and calls start on the JavaFX application thread.
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Loads the login layout and displays the application's first window.
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        primaryStage.setTitle("TindaTrack");
        primaryStage.setMinWidth(460);
        primaryStage.setMinHeight(540);
        showLogin(primaryStage);
        primaryStage.show();
    }

    private void showLogin(Stage stage) throws IOException {
        sessions.clear();
        FXMLLoader loader = loadView("login.fxml");
        LoginController controller = loader.getController();
        controller.configure(sessions, owner -> {
            try {
                showOwnerHome(stage, owner);
            } catch (IOException exception) {
                controller.showScreenError("the owner screen");
            }
        }, () -> {
            try {
                showGuestMode(stage);
            } catch (IOException exception) {
                controller.showScreenError("Guest Mode");
            }
        });
        if (stage.getScene() == null) {
            stage.setScene(createScene(loader.getRoot()));
        } else {
            stage.getScene().setRoot(loader.getRoot());
        }
    }

    private void showOwnerHome(Stage stage, OwnerSession owner) throws IOException {
        FXMLLoader loader = loadView("owner-home.fxml");
        OwnerHomeController controller = loader.getController();

        sessions.begin(owner);
        controller.configure(owner.fullName(), () -> returnToLogin(stage));
        stage.getScene().setRoot(loader.getRoot());
    }

    private void showGuestMode(Stage stage) throws IOException {
        FXMLLoader loader = loadView("guest.fxml");
        GuestController controller = loader.getController();

        GuestSession guest = new GuestSession();
        sessions.begin(guest);
        controller.configure(new GuestService(sessions, guest), () -> returnToLogin(stage));
        stage.getScene().setRoot(loader.getRoot());
    }

    private void returnToLogin(Stage stage) {
        try {
            showLogin(stage);
        } catch (IOException exception) {
            stage.close();
        }
    }

    private FXMLLoader loadView(String fileName) throws IOException {
        URL fxmlUrl = getClass().getResource("/tindatrack/fxml/" + fileName);
        if (fxmlUrl == null) {
            throw new IOException("The " + fileName + " layout could not be found.");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        loader.load();
        return loader;
    }

    private Scene createScene(Parent root) throws IOException {
        URL cssUrl = getClass().getResource("/tindatrack/css/login.css");
        if (cssUrl == null) {
            throw new IOException("The login stylesheet could not be found.");
        }
        Scene scene = new Scene(root, 640, 560);
        scene.getStylesheets().add(cssUrl.toExternalForm());
        return scene;
    }
}
