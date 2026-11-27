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
    private Runnable onExpiringSoon;
    private Runnable onCredit;
    private Runnable onLogout;

    public void configure(String ownerName, Runnable onInventory,
                          Runnable onExpiringSoon, Runnable onCredit, Runnable onLogout) {
        ownerGreeting.setText("Welcome, " + ownerName + "!");
        this.onInventory = onInventory;
        this.onExpiringSoon = onExpiringSoon;
        this.onCredit = onCredit;
        this.onLogout = onLogout;
    }

    public void showNavigationError(String message) {
        navigationMessage.setText(message);
        navigationMessage.setManaged(true);
        navigationMessage.setVisible(true);
    }

    @FXML
    private void handleInventory() {
        clearNavigationError();
        onInventory.run();
    }

    @FXML
    private void handleExpiringSoon() {
        clearNavigationError();
        onExpiringSoon.run();
    }

    @FXML
    private void handleCredit() {
        clearNavigationError();
        onCredit.run();
    }

    private void clearNavigationError() {
        navigationMessage.setVisible(false);
        navigationMessage.setManaged(false);
    }

    @FXML
    private void handleLogout() {
        onLogout.run();
    }
}
