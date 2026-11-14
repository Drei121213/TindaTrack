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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tindatrack.core.SessionState.AccessDeniedException;

/** Displays stored products and opens the Add Product form. */
public final class InventoryController {

    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, Number> productId;
    @FXML private TableColumn<Product, String> productName;
    @FXML private TableColumn<Product, BigDecimal> productPrice;
    @FXML private TableColumn<Product, Number> productStock;
    @FXML private TableColumn<Product, LocalDate> productExpiration;
    @FXML private Button addProductButton;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private TextField searchField;
    @FXML private Label statusLabel;

    private ProductDao products;
    private Runnable onBack;
    private String submittedSearch = "";

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

    public void configure(ProductDao products, Runnable onBack) {
        this.products = products;
        this.onBack = onBack;
        loadProducts(false);
    }

    public void showNavigationError(String message) {
        statusLabel.setText(message);
    }

    @FXML
    private void handleSearch() {
        submittedSearch = searchField.getText().strip();
        loadProducts(false);
    }

    @FXML
    private void handleAddProduct() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tindatrack/fxml/product-form.fxml"));
            Scene scene = new Scene(loader.load(), 500, 550);
            scene.getStylesheets().addAll(productsTable.getScene().getStylesheets());
            ProductFormController form = loader.getController();
            form.configure(products);

            Stage dialog = new Stage();
            dialog.initOwner(productsTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("TindaTrack - Add Product");
            dialog.setScene(scene);
            dialog.setMinWidth(420);
            dialog.setMinHeight(420);
            dialog.setOnCloseRequest(event -> {
                if (form.isSaving()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            if (form.wasSaved()) {
                searchField.clear();
                submittedSearch = "";
                loadProducts(true);
            }
        } catch (IOException exception) {
            statusLabel.setText("Could not open Add Product. Please try again.");
        }
    }

    private void loadProducts(boolean afterSave) {
        String search = submittedSearch;
        statusLabel.setText(afterSave ? "Product saved. Refreshing inventory..." : "Loading products...");
        productsTable.setPlaceholder(new Label("Loading products..."));
        setLoading(true);

        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return search.isEmpty() ? products.findAll() : products.findByName(search);
            }
        };
        task.setOnSucceeded(event -> {
            productsTable.getItems().setAll(task.getValue());
            String emptyMessage = search.isEmpty() ? "No products to display yet." : "No products found.";
            productsTable.setPlaceholder(new Label(emptyMessage));
            statusLabel.setText(afterSave ? "Product saved." : "");
            setLoading(false);
        });
        task.setOnFailed(event -> {
            if (task.getException() instanceof AccessDeniedException denied) {
                productsTable.getItems().clear();
                statusLabel.setText(denied.getMessage());
            } else {
                String message = afterSave ? "Product saved. Could not refresh the inventory."
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
        loadProducts(false);
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }
}
