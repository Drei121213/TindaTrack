package tindatrack.core;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Displays the signed-in owner and forwards logout to the application. */
public class OwnerHomeController {

    @FXML
    private Label ownerGreeting;

    private Runnable onLogout;

    public void configure(String ownerName, Runnable onLogout) {
        ownerGreeting.setText("Welcome, " + ownerName + "!");
        this.onLogout = onLogout;
    }

    @FXML
    private void handleLogout() {
        onLogout.run();
    }
}
