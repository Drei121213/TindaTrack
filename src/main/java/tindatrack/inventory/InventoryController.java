package tindatrack.inventory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tindatrack.core.SessionState.AccessDeniedException;

/** Displays stored products and handles owner inventory actions. */
public final class InventoryController {

    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, Number> productId;
    @FXML private TableColumn<Product, String> productName;
    @FXML private TableColumn<Product, BigDecimal> productPrice;
    @FXML private TableColumn<Product, Number> productStock;
    @FXML private TableColumn<Product, LocalDate> productExpiration;
    @FXML private Button addProductButton;
    @FXML private Button editProductButton;
    @FXML private Button deleteProductButton;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private TextField searchField;
    @FXML private Label statusLabel;

    private ProductDao products;
    private String submittedSearch = "";
    private Integer selectedProductId;
    private boolean active;
    private Task<List<Product>> currentLoad;
    private Task<ProductDao.DeleteResult> deletion;

    @FXML
    private void initialize() {
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        productId.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().id()));
        productName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        productPrice.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().price()));
        productStock.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().stockQuantity()));
        productExpiration.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().expirationDate()));
    }

    public void configure(ProductDao products) {
        this.products = products;
    }

    public void activate() {
        products.requireOwnerAccess();
        active = true;
        loadProducts("");
    }

    public void deactivate() {
        rememberSelection();
        active = false;
        if (currentLoad != null) {
            currentLoad.cancel();
            currentLoad = null;
        }
        productsTable.getItems().clear();
    }

    /** A submitted delete must finish before navigation or logout. */
    public boolean isDeleting() {
        return deletion != null;
    }

    private void rememberSelection() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected != null) selectedProductId = selected.id();
    }

    @FXML
    private void handleSearch() {
        submittedSearch = searchField.getText().strip();
        loadProducts("");
    }

    @FXML
    private void handleAddProduct() {
        openProductForm(null);
    }

    private void openProductForm(Product original) {
        String title = original == null ? "Add Product" : "Edit Product";
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tindatrack/fxml/product-form.fxml"));
            Scene scene = new Scene(loader.load(), 500, 550);
            scene.getStylesheets().addAll(productsTable.getScene().getStylesheets());
            ProductFormController form = loader.getController();
            form.configure(products, original);

            Stage dialog = new Stage();
            dialog.initOwner(productsTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("TindaTrack - " + title);
            dialog.setScene(scene);
            dialog.setMinWidth(420);
            dialog.setMinHeight(420);
            dialog.setOnCloseRequest(event -> {
                if (form.isSaving()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            if (!active) return;
            if (form.wasSaved()) {
                if (original == null) {
                    searchField.clear();
                    submittedSearch = "";
                }
                loadProducts(original == null ? "Product saved." : "Changes saved.");
            }
        } catch (IOException exception) {
            statusLabel.setText("Could not open " + title + ". Please try again.");
        }
    }

    /**
     * Loads the last submitted search on a background task while inventory actions
     * are disabled. Ordinary failures retain earlier rows; an ended session clears them.
     * @param successMessage optional completed-action message to retain during refresh
     */
    private void loadProducts(String successMessage) {
        if (!active) return;
        rememberSelection();
        if (currentLoad != null) currentLoad.cancel();
        String search = submittedSearch;
        statusLabel.setText(successMessage.isEmpty() ? "Loading products..."
                : successMessage + " Refreshing inventory...");
        productsTable.setPlaceholder(new Label("Loading products..."));
        setLoading(true);

        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return search.isEmpty() ? products.findAll() : products.findByName(search);
            }
        };
        currentLoad = task;
        task.setOnSucceeded(event -> {
            if (!active || currentLoad != task) return;
            currentLoad = null;
            try {
                products.requireOwnerAccess();
                productsTable.getItems().setAll(task.getValue());
                productsTable.sort();
                Product selected = productsTable.getItems().stream()
                        .filter(p -> selectedProductId != null && p.id() == selectedProductId)
                        .findFirst().orElse(null);
                productsTable.getSelectionModel().select(selected);
                selectedProductId = selected == null ? null : selected.id();
                if (selected != null) productsTable.scrollTo(selected);
                String emptyMessage = search.isEmpty() ? "No products to display yet." : "No products found.";
                productsTable.setPlaceholder(new Label(emptyMessage));
                statusLabel.setText(successMessage);
                setLoading(false);
            } catch (AccessDeniedException denied) {
                showAccessDenied(denied);
            }
        });
        task.setOnFailed(event -> {
            if (!active || currentLoad != task) return;
            currentLoad = null;
            if (task.getException() instanceof AccessDeniedException denied) {
                showAccessDenied(denied);
                return;
            } else {
                String message = !successMessage.isEmpty()
                        ? successMessage + " Could not refresh the inventory."
                        : search.isEmpty() ? "Could not load products. Please try again."
                        : "Search failed. Please try again.";
                if (!productsTable.getItems().isEmpty()) {
                    message += " Showing previous results.";
                }
                statusLabel.setText(message);
            }
            productsTable.setPlaceholder(new Label("Use Refresh to try again."));
            setLoading(false);
        });

        Thread worker = new Thread(task, "tindatrack-reload-products");
        worker.setDaemon(true);
        worker.start();
    }

    private void setLoading(boolean loading) {
        searchField.setDisable(loading);
        searchButton.setDisable(loading);
        refreshButton.setDisable(loading);
        addProductButton.setDisable(loading);
        editProductButton.setDisable(loading);
        deleteProductButton.setDisable(loading);
    }

    private void showAccessDenied(AccessDeniedException denied) {
        productsTable.getItems().clear();
        statusLabel.setText(denied.getMessage());
        setLoading(true);
    }

    @FXML
    private void handleEditProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a product to edit.");
            return;
        }
        openProductForm(selected);
    }

    @FXML
    private void handleDeleteProduct() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a product to delete.");
            return;
        }

        ButtonType delete = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete “" + selected.name() + "” from inventory?", delete, ButtonType.CANCEL);
        confirmation.initOwner(productsTable.getScene().getWindow());
        confirmation.setTitle("Delete Product");
        confirmation.setHeaderText("This product will be removed from inventory.");
        ((Button) confirmation.getDialogPane().lookupButton(delete)).setDefaultButton(false);
        ((Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != delete) {
            return;
        }

        setLoading(true);
        statusLabel.setText("Deleting product...");
        Task<ProductDao.DeleteResult> task = new Task<>() {
            @Override
            protected ProductDao.DeleteResult call() throws Exception {
                return products.deleteProduct(selected);
            }
        };
        deletion = task;
        task.setOnSucceeded(event -> {
            deletion = null;
            if (!active) return;
            try {
                products.requireOwnerAccess();
            } catch (AccessDeniedException denied) {
                showAccessDenied(denied);
                return;
            }
            switch (task.getValue()) {
                case DELETED -> {
                    productsTable.getItems().remove(selected);
                    selectedProductId = null;
                    loadProducts("Product deleted.");
                }
                case STALE -> {
                    setLoading(false);
                    statusLabel.setText("This product changed or was removed. Refresh Inventory before trying again.");
                }
                case REFERENCED -> {
                    setLoading(false);
                    statusLabel.setText("This product cannot be deleted because another record references it.");
                }
            }
        });
        task.setOnFailed(event -> {
            deletion = null;
            if (!active) return;
            setLoading(false);
            String message = task.getException() instanceof AccessDeniedException denied
                    ? denied.getMessage() : "Product was not deleted. Please try again.";
            statusLabel.setText(message);
        });

        Thread worker = new Thread(task, "tindatrack-delete-product");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleRefresh() {
        loadProducts("");
    }

}
