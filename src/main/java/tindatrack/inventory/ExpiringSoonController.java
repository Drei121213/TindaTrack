package tindatrack.inventory;

import java.time.LocalDate;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import tindatrack.core.SessionState.AccessDeniedException;

/** Displays expiration dates for the active owner without blocking JavaFX. */
public final class ExpiringSoonController {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final PseudoClass EXPIRED = PseudoClass.getPseudoClass("expired");

    @FXML private TableView<Product> expiringTable;
    @FXML private TableColumn<Product, String> productName;
    @FXML private TableColumn<Product, Number> stock;
    @FXML private TableColumn<Product, LocalDate> expirationDate;
    @FXML private TableColumn<Product, String> daysLeft;
    @FXML private ComboBox<Integer> rangeSelector;
    @FXML private Label rangeLabel;
    @FXML private Label listMessage;
    @FXML private Button refreshButton;
    @FXML private Label navigationMessage;

    private ProductDao products;
    private Runnable onBack;
    private Runnable onInventory;
    private LocalDate referenceDate;
    private Task<List<Product>> currentLoad;

    @FXML
    private void initialize() {
        navigationMessage.managedProperty().bind(navigationMessage.visibleProperty());
        rangeSelector.getItems().setAll(DEFAULT_RANGE_DAYS, 14, 30);
        rangeSelector.setValue(DEFAULT_RANGE_DAYS);
        rangeSelector.valueProperty().addListener((property, oldRange, newRange) -> {
            if (products != null && newRange != null) {
                loadProducts();
            }
        });
        expiringTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        expiringTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                // TableView reuses rows, so also remove the highlight when a row changes.
                pseudoClassStateChanged(EXPIRED, !empty && product != null
                        && product.expirationDate().isBefore(referenceDate));
            }
        });
        productName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        stock.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().stockQuantity()));
        expirationDate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().expirationDate()));
        daysLeft.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                ExpirationDates.status(cell.getValue().expirationDate(), referenceDate)));
    }

    public void configure(ProductDao products, Runnable onBack, Runnable onInventory) {
        this.products = products;
        this.onBack = onBack;
        this.onInventory = onInventory;
        loadProducts();
    }

    public void showNavigationError(String message) {
        navigationMessage.setText(message);
        navigationMessage.setVisible(true);
    }

    @FXML
    private void handleBack() {
        onBack.run();
    }

    @FXML
    private void handleInventory() {
        onInventory.run();
    }

    /**
     * Loads expired products and dates through today plus the selected day range.
     * Cancels the previous task and ignores callbacks from superseded loads.
     * Uses the captured query date for labels so all rows share one reference day.
     */
    @FXML
    private void loadProducts() {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(rangeSelector.getValue());
        if (currentLoad != null) {
            currentLoad.cancel();
        }
        rangeLabel.setText("Expired products and dates from " + today + " through " + cutoff);
        navigationMessage.setVisible(false);
        refreshButton.setText("Refresh");
        refreshButton.setDisable(true);
        listMessage.setText("Loading products...");
        expiringTable.getItems().clear();

        Task<List<Product>> load = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return products.findExpiringThrough(cutoff);
            }
        };
        currentLoad = load;
        load.setOnSucceeded(event -> {
            if (currentLoad != load) {
                return;
            }
            currentLoad = null;
            referenceDate = today;
            listMessage.setText("No products expiring soon.");
            expiringTable.getItems().setAll(load.getValue());
            refreshButton.setDisable(false);
        });
        load.setOnFailed(event -> {
            if (currentLoad != load) {
                return;
            }
            currentLoad = null;
            if (load.getException() instanceof AccessDeniedException denied) {
                listMessage.setText(denied.getMessage());
            } else {
                listMessage.setText("Cannot load the list. Please try again.");
                refreshButton.setText("Retry");
                refreshButton.setDisable(false);
            }
        });

        Thread worker = new Thread(load, "tindatrack-load-expiring-products");
        worker.setDaemon(true);
        worker.start();
    }

}
