package tindatrack.inventory;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

/** Handles navigation and feedback on the inventory screen. */
public final class InventoryController {

    @FXML
    private TableView<?> productsTable;

    @FXML
    private Label statusLabel;

    private Runnable onBack;

    @FXML
    private void initialize() {
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    public void configure(Runnable onBack) {
        this.onBack = onBack;
    }

    public void showNavigationError(String message) {
        statusLabel.setText(message);
    }

    @FXML
    private void handleSearch() {
        statusLabel.setText("Product search is coming soon.");
    }

    @FXML
    private void handleAddProduct() {
        statusLabel.setText("Adding products is coming soon.");
    }

    @FXML
    private void handleEditProduct() {
        statusLabel.setText("Editing products is coming soon.");
    }

    @FXML
    private void handleDeleteProduct() {
        statusLabel.setText("Deleting products is coming soon.");
    }

    @FXML
    private void handleRefresh() {
        statusLabel.setText("Refreshing products is coming soon.");
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }
}
