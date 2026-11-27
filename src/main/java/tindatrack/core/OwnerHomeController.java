package tindatrack.core;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Keeps the existing owner greeting; navigation comes from the shared sidebar. */
public class OwnerHomeController {

    @FXML
    private Label ownerGreeting;

    public void configure(String ownerName) {
        ownerGreeting.setText("Welcome, " + ownerName + "!");
    }
}
