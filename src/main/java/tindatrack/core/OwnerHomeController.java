package tindatrack.core;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Displays the signed-in owner and forwards navigation to the application. */
public class OwnerHomeController {

    @FXML
    private Label ownerGreeting;

    @FXML
    private Label navigationMessage;

    private Runnable onInventory;
    private Runnable onLogout;

    public void configure(String ownerName, Runnable onInventory, Runnable onLogout) {
        ownerGreeting.setText("Welcome, " + ownerName + "!");
        this.onInventory = onInventory;
        this.onLogout = onLogout;
    }

    public void showNavigationError(String message) {
        navigationMessage.setText(message);
        navigationMessage.setManaged(true);
        navigationMessage.setVisible(true);
    }

    @FXML
    private void handleInventory() {
        navigationMessage.setVisible(false);
        navigationMessage.setManaged(false);
        onInventory.run();
    }

    @FXML
    private void handleLogout() {
        onLogout.run();
    }
}
