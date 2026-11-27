package tindatrack.core;

import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/** Shared sidebar; routing and permission checks remain in the application. */
public final class OwnerNavigationController {
    public enum Destination { DASHBOARD, INVENTORY, CREDIT, EXPIRING }

    @FXML private Label ownerName;
    @FXML private Label navigationMessage;
    @FXML private Button dashboardButton;
    @FXML private Button inventoryButton;
    @FXML private Button creditButton;
    @FXML private Button expiringButton;
    private Consumer<Destination> onNavigate;
    private Runnable onLogout;

    public void configure(String name, Consumer<Destination> onNavigate, Runnable onLogout) {
        ownerName.setText(name);
        this.onNavigate = onNavigate;
        this.onLogout = onLogout;
        navigationMessage.managedProperty().bind(navigationMessage.visibleProperty());
    }

    public void setCurrent(Destination destination) {
        dashboardButton.setDisable(destination == Destination.DASHBOARD);
        inventoryButton.setDisable(destination == Destination.INVENTORY);
        creditButton.setDisable(destination == Destination.CREDIT);
        expiringButton.setDisable(destination == Destination.EXPIRING);
        navigationMessage.setVisible(false);
    }

    public void showError(String message) {
        navigationMessage.setText(message);
        navigationMessage.setVisible(true);
    }

    @FXML private void openDashboard() { onNavigate.accept(Destination.DASHBOARD); }
    @FXML private void openInventory() { onNavigate.accept(Destination.INVENTORY); }
    @FXML private void openCredit() { onNavigate.accept(Destination.CREDIT); }
    @FXML private void openExpiring() { onNavigate.accept(Destination.EXPIRING); }
    @FXML private void logout() { onLogout.run(); }
}
