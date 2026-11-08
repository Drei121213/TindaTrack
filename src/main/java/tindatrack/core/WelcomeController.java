package tindatrack.core;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controls the simple welcome view. No store features are implemented here.
 */
public class WelcomeController {

    @FXML
    private Label welcomeMessage;

    /**
     * Called by FXMLLoader after the layout and its fields have been loaded.
     */
    @FXML
    private void initialize() {
        welcomeMessage.setText("Welcome to TindaTrack");
    }
}
