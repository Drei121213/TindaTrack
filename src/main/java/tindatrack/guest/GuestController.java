package tindatrack.guest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;

import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import tindatrack.core.SessionState.AccessDeniedException;
import tindatrack.guest.GuestService.BalanceRow;
import tindatrack.guest.GuestService.ProductRow;

/** Presents guest data and keeps database work off the JavaFX thread. */
public final class GuestController {

    @FXML private TabPane guestTabs;
    @FXML private Tab creditTab;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button refreshCreditButton;
    @FXML private TableView<ProductRow> productsTable;
    @FXML private TableColumn<ProductRow, String> productName;
    @FXML private TableColumn<ProductRow, BigDecimal> productPrice;
    @FXML private TableColumn<ProductRow, Number> productStock;
    @FXML private TableView<BalanceRow> balancesTable;
    @FXML private TableColumn<BalanceRow, String> customerName;
    @FXML private TableColumn<BalanceRow, BigDecimal> customerBalance;

    private GuestService service;
    private Runnable onExit;

    @FXML
    private void initialize() {
        productName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        productPrice.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().price()));
        productStock.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().stockQuantity()));
        customerName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().customerName()));
        customerBalance.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().balance()));
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        balancesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        guestTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (selected == creditTab) {
                loadBalances();
            }
        });
    }

    public void configure(GuestService service, Runnable onExit) {
        this.service = service;
        this.onExit = onExit;
        searchProducts();
    }

    @FXML
    private void searchProducts() {
        String search = searchField.getText();
        loadRows(productsTable, searchButton, () -> service.findProducts(search), "No products found.");
    }

    @FXML
    private void loadBalances() {
        loadRows(balancesTable, refreshCreditButton, service::findBalances, "No customer balances found.");
    }

    private <T> void loadRows(TableView<T> table, Button action,
                              Callable<List<T>> query, String emptyMessage) {
        if (action.isDisabled()) {
            return;
        }
        action.setDisable(true);
        table.getItems().clear();
        table.setPlaceholder(new Label("Loading..."));

        Task<List<T>> load = new Task<>() {
            @Override
            protected List<T> call() throws Exception {
                return query.call();
            }
        };
        load.setOnSucceeded(event -> {
            table.getItems().setAll(load.getValue());
            table.setPlaceholder(new Label(emptyMessage));
            action.setDisable(false);
        });
        load.setOnFailed(event -> {
            Throwable failure = load.getException();
            String message = failure instanceof AccessDeniedException denied
                    ? denied.getMessage() : "Cannot load the data. Please try again.";
            table.setPlaceholder(new Label(message));
            action.setDisable(false);
        });

        Thread worker = new Thread(load, "tindatrack-guest-query");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleExitGuest() {
        onExit.run();
    }
}
