package tindatrack.inventory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import tindatrack.core.SessionState.AccessDeniedException;

/** Displays expiration dates for the active owner without blocking JavaFX. */
public final class ExpiringSoonController {

    private static final int DEFAULT_RANGE_DAYS = 7;

    @FXML private TableView<Product> expiringTable;
    @FXML private TableColumn<Product, String> productName;
    @FXML private TableColumn<Product, Number> stock;
    @FXML private TableColumn<Product, LocalDate> expirationDate;
    @FXML private TableColumn<Product, String> daysLeft;
    @FXML private Label rangeLabel;
    @FXML private Label listMessage;
    @FXML private Button retryButton;

    private ProductDao products;
    private LocalDate referenceDate;

    @FXML
    private void initialize() {
        retryButton.managedProperty().bind(retryButton.visibleProperty());
        expiringTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        productName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        stock.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().stockQuantity()));
        expirationDate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().expirationDate()));
        daysLeft.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                describeDaysLeft(ChronoUnit.DAYS.between(referenceDate, cell.getValue().expirationDate()))));
    }

    public void configure(ProductDao products) {
        this.products = products;
        loadProducts();
    }

    @FXML
    private void loadProducts() {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(DEFAULT_RANGE_DAYS);
        rangeLabel.setText("Expired products and dates from " + today + " through " + cutoff);
        retryButton.setVisible(false);
        listMessage.setText("Loading products...");
        expiringTable.getItems().clear();

        Task<List<Product>> load = new Task<>() {
            @Override
            protected List<Product> call() throws Exception {
                return products.findExpiringThrough(cutoff);
            }
        };
        load.setOnSucceeded(event -> {
            referenceDate = today;
            listMessage.setText("No products expiring soon.");
            expiringTable.getItems().setAll(load.getValue());
        });
        load.setOnFailed(event -> {
            if (load.getException() instanceof AccessDeniedException denied) {
                listMessage.setText(denied.getMessage());
            } else {
                listMessage.setText("Cannot load the list. Please try again.");
                retryButton.setVisible(true);
            }
        });

        Thread worker = new Thread(load, "tindatrack-load-expiring-products");
        worker.setDaemon(true);
        worker.start();
    }

    private String describeDaysLeft(long days) {
        if (days < 0) {
            return "Expired";
        }
        if (days == 0) {
            return "Expires Today";
        }
        return days == 1 ? "1 Day Left" : days + " Days Left";
    }
}
