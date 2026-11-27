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
import tindatrack.core.TableFormats;

/** Displays expiration dates for the active owner without blocking JavaFX. */
public final class ExpiringSoonController {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final PseudoClass EXPIRED = PseudoClass.getPseudoClass("expired");
    private static final PseudoClass EXPIRING = PseudoClass.getPseudoClass("expiring");

    @FXML private TableView<Product> expiringTable;
    @FXML private TableColumn<Product, String> productName;
    @FXML private TableColumn<Product, Number> stock;
    @FXML private TableColumn<Product, LocalDate> expirationDate;
    @FXML private TableColumn<Product, String> daysLeft;
    @FXML private ComboBox<Integer> rangeSelector;
    @FXML private Label rangeLabel;
    @FXML private Label listMessage;
    @FXML private Button refreshButton;

    private ProductDao products;
    private boolean active;
    private Integer selectedProductId;
    private LocalDate referenceDate;
    private Task<List<Product>> currentLoad;

    @FXML
    private void initialize() {
        rangeSelector.getItems().setAll(DEFAULT_RANGE_DAYS, 14, 30);
        rangeSelector.setValue(DEFAULT_RANGE_DAYS);
        rangeSelector.valueProperty().addListener((property, oldRange, newRange) -> {
            if (active && products != null && newRange != null) {
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
                pseudoClassStateChanged(EXPIRING, !empty && product != null
                        && !product.expirationDate().isBefore(referenceDate));
            }
        });
        productName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        stock.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().stockQuantity()));
        expirationDate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().expirationDate()));
        daysLeft.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                ExpirationDates.status(cell.getValue().expirationDate(), referenceDate)));
        TableFormats.text(productName);
        TableFormats.date(expirationDate);
    }

    public void configure(ProductDao products) {
        this.products = products;
    }

    public void activate() {
        products.requireOwnerAccess();
        active = true;
        loadProducts();
    }

    public void deactivate() {
        rememberSelection();
        active = false;
        if (currentLoad != null) {
            currentLoad.cancel();
            currentLoad = null;
        }
        expiringTable.getItems().clear();
    }

    private void rememberSelection() {
        Product selected = expiringTable.getSelectionModel().getSelectedItem();
        if (selected != null) selectedProductId = selected.id();
    }

    /**
     * Loads expired products and dates through today plus the selected day range.
     * Cancels the previous task and ignores callbacks from superseded loads.
     * Uses the captured query date for labels so all rows share one reference day.
     */
    @FXML
    private void loadProducts() {
        if (!active) return;
        rememberSelection();
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(rangeSelector.getValue());
        if (currentLoad != null) {
            currentLoad.cancel();
        }
        rangeLabel.setText("Expired products and dates from " + today + " through " + cutoff);
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
            if (!active || currentLoad != load) {
                return;
            }
            currentLoad = null;
            try {
                products.requireOwnerAccess();
                referenceDate = today;
                listMessage.setText("No products expiring soon.");
                expiringTable.getItems().setAll(load.getValue());
                Product selected = expiringTable.getItems().stream()
                        .filter(p -> selectedProductId != null && p.id() == selectedProductId)
                        .findFirst().orElse(null);
                expiringTable.getSelectionModel().select(selected);
                selectedProductId = selected == null ? null : selected.id();
                if (selected != null) expiringTable.scrollTo(selected);
                refreshButton.setDisable(false);
            } catch (AccessDeniedException denied) {
                listMessage.setText(denied.getMessage());
            }
        });
        load.setOnFailed(event -> {
            if (!active || currentLoad != load) {
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
