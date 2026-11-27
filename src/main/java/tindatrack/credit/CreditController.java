package tindatrack.credit;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tindatrack.core.SessionState.AccessDeniedException;

/** Displays owner credit information and opens the utang/payment forms. */
public final class CreditController {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, String> customerName;
    @FXML private TableColumn<Customer, BigDecimal> customerBalance;
    @FXML private TableColumn<Customer, LocalDateTime> lastTransaction;
    @FXML private TableColumn<Customer, Void> customerActions;
    @FXML private TableView<CreditTransaction> historyTable;
    @FXML private TableColumn<CreditTransaction, LocalDateTime> historyDate;
    @FXML private TableColumn<CreditTransaction, String> historyType;
    @FXML private TableColumn<CreditTransaction, String> historyItems;
    @FXML private TableColumn<CreditTransaction, BigDecimal> historyAmount;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private Button recordUtangButton;
    @FXML private Label customerPlaceholder;
    @FXML private Label historyPlaceholder;
    @FXML private Label historyTitle;
    @FXML private Label statusLabel;

    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final ObservableList<CreditTransaction> history = FXCollections.observableArrayList();
    private CreditDao credits;
    private boolean active;
    private Integer selectedCustomerId;
    private Task<List<Customer>> customerLoad;
    private Task<List<CreditTransaction>> historyLoad;
    private Task<Optional<Customer>> paymentCustomerLoad;

    @FXML
    private void initialize() {
        customersTable.setItems(customers);
        historyTable.setItems(history);
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        customerName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        customerBalance.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().balance()));
        lastTransaction.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().lastTransaction()));
        historyDate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().date()));
        historyType.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().type()));
        historyItems.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().items() == null ? "—" : cell.getValue().items()));
        historyAmount.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().amount()));
        formatMoney(customerBalance);
        formatMoney(historyAmount);
        formatDate(lastTransaction);
        formatDate(historyDate);

        customerActions.setCellFactory(column -> new TableCell<>() {
            private final Button payment = new Button("Record Payment");
            {
                payment.getStyleClass().add("credit-payment-button");
                payment.setOnAction(event -> handleRecordPayment(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : payment);
            }
        });
        customersTable.getSelectionModel().selectedItemProperty().addListener(
                (property, previous, selected) -> {
                    if (selected != null) selectedCustomerId = selected.id();
                    if (active) loadHistory(selected);
                });
    }

    public void configure(CreditDao credits) {
        this.credits = credits;
    }

    public void activate() {
        credits.requireOwnerAccess();
        active = true;
        loadCustomers("", selectedCustomerId);
    }

    public void deactivate() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        if (selected != null) selectedCustomerId = selected.id();
        active = false;
        cancelLoads();
        customers.clear();
        clearHistory();
    }

    @FXML
    private void handleRecordUtang() {
        try {
            credits.requireOwnerAccess();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tindatrack/fxml/record-utang.fxml"));
            Scene scene = new Scene(loader.load(), 520, 620);
            scene.getStylesheets().addAll(customersTable.getScene().getStylesheets());
            RecordUtangController form = loader.getController();
            form.configure(new CreditService(credits), customersTable.getSelectionModel().getSelectedItem());
            Stage dialog = new Stage();
            dialog.initOwner(customersTable.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("TindaTrack - Record Utang");
            dialog.setScene(scene);
            dialog.setMinWidth(420);
            dialog.setMinHeight(460);
            dialog.setOnCloseRequest(event -> {
                if (form.isSaving()) {
                    event.consume();
                }
            });
            dialog.showAndWait();
            if (!active) return;
            if (form.savedCustomerId() != null) {
                searchField.clear();
                loadCustomers("Utang saved.", form.savedCustomerId());
            }
        } catch (IOException exception) {
            statusLabel.setText("Could not open Record Utang. Please try again.");
        } catch (AccessDeniedException denied) {
            showAccessDenied(denied);
        }
    }

    /** Reads the current balance before opening SB09, including the no-balance case. */
    private void handleRecordPayment(Customer selected) {
        if (!active || selected == null || customerLoad != null || paymentCustomerLoad != null) {
            return;
        }
        try {
            credits.requireOwnerAccess();
        } catch (AccessDeniedException denied) {
            showAccessDenied(denied);
            return;
        }
        setLoading(true);
        statusLabel.setText("Checking the customer's current balance...");
        Task<Optional<Customer>> load = new Task<>() {
            @Override
            protected Optional<Customer> call() throws Exception {
                return credits.findCustomer(selected.id());
            }
        };
        paymentCustomerLoad = load;
        load.setOnSucceeded(event -> {
            if (!active || paymentCustomerLoad != load) {
                return;
            }
            paymentCustomerLoad = null;
            setLoading(false);
            try {
                credits.requireOwnerAccess();
                if (load.getValue().isEmpty()) {
                    statusLabel.setText("This customer was removed. Refresh the credit list.");
                    return;
                }
                Customer current = load.getValue().get();
                int index = customers.indexOf(selected);
                if (index >= 0) {
                    customers.set(index, current);
                }
                CreditRules.requireOutstandingBalance(current.balance());
                statusLabel.setText("");
                openPaymentForm(current);
            } catch (CreditRules.ValidationException invalid) {
                statusLabel.setText(invalid.getMessage());
            } catch (AccessDeniedException denied) {
                showAccessDenied(denied);
            } catch (IOException exception) {
                statusLabel.setText("Could not open Record Payment. Please try again.");
            }
        });
        load.setOnFailed(event -> {
            if (!active || paymentCustomerLoad != load) {
                return;
            }
            paymentCustomerLoad = null;
            if (load.getException() instanceof AccessDeniedException denied) {
                showAccessDenied(denied);
            } else {
                setLoading(false);
                statusLabel.setText("Cannot load the customer's balance. Please try again.");
            }
        });
        start(load, "tindatrack-payment-balance");
    }

    private void openPaymentForm(Customer customer) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/tindatrack/fxml/record-payment.fxml"));
        Scene scene = new Scene(loader.load(), 520, 540);
        scene.getStylesheets().addAll(customersTable.getScene().getStylesheets());
        RecordPaymentController form = loader.getController();
        form.configure(new CreditService(credits), customer);
        Stage dialog = new Stage();
        dialog.initOwner(customersTable.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("TindaTrack - Record Payment");
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
        if (form.savedBalance() != null) {
            searchField.clear();
            loadCustomers("Payment saved. Remaining balance: ₱" + form.savedBalance().toPlainString(), customer.id());
        } else {
            // Another payment may have changed the balance while this form was open.
            loadCustomers("", customer.id());
        }
    }

    /** Search and Refresh both reload the currently entered name fragment. */
    @FXML
    private void loadCustomers() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        Integer restoreId = selectedCustomerId;
        if (selected != null) restoreId = selected.id();
        loadCustomers("", restoreId);
    }

    private void loadCustomers(String successMessage, Integer selectCustomerId) {
        if (!active || credits == null) {
            return;
        }
        cancelLoads();
        selectedCustomerId = selectCustomerId;
        String search = searchField.getText().strip();
        customers.clear();
        clearHistory();
        setLoading(true);
        customerPlaceholder.setText("Loading customers...");
        statusLabel.setText(successMessage.isEmpty() ? "Loading customer balances..."
                : successMessage + " Refreshing balances...");

        Task<List<Customer>> load = new Task<>() {
            @Override
            protected List<Customer> call() throws Exception {
                return credits.findCustomers(search);
            }
        };
        customerLoad = load;
        load.setOnSucceeded(event -> {
            if (!active || customerLoad != load) {
                return;
            }
            customerLoad = null;
            try {
                credits.requireOwnerAccess();
                customers.setAll(load.getValue());
                customersTable.sort();
                customerPlaceholder.setText(search.isEmpty()
                        ? "No customers recorded yet." : "No customers found.");
                statusLabel.setText((successMessage.isEmpty() ? "" : successMessage + " ")
                        + customers.size() + " customer(s). Select a customer to view history.");
                setLoading(false);
                if (selectCustomerId != null) {
                    Customer selected = customers.stream().filter(c -> c.id() == selectCustomerId)
                            .findFirst().orElse(null);
                    customersTable.getSelectionModel().select(selected);
                    selectedCustomerId = selected == null ? null : selected.id();
                    if (selected != null) customersTable.scrollTo(selected);
                }
            } catch (AccessDeniedException denied) {
                showAccessDenied(denied);
            }
        });
        load.setOnFailed(event -> {
            if (!active || customerLoad != load) {
                return;
            }
            customerLoad = null;
            if (load.getException() instanceof AccessDeniedException denied) {
                showAccessDenied(denied);
            } else {
                customerPlaceholder.setText("Cannot load customers. Please try again.");
                statusLabel.setText((successMessage.isEmpty() ? "" : successMessage + " ")
                        + "Could not load customer balances. Select Refresh to retry.");
                setLoading(false);
            }
        });
        start(load, "tindatrack-load-customers");
    }

    private void loadHistory(Customer customer) {
        cancelHistory();
        clearHistory();
        if (!active || customer == null) {
            return;
        }
        historyTitle.setText("History — " + customer.name());
        historyPlaceholder.setText("Loading history...");
        Task<List<CreditTransaction>> load = new Task<>() {
            @Override
            protected List<CreditTransaction> call() throws Exception {
                return credits.findHistory(customer.id());
            }
        };
        historyLoad = load;
        load.setOnSucceeded(event -> {
            if (!active || historyLoad != load) {
                return;
            }
            historyLoad = null;
            try {
                credits.requireOwnerAccess();
                history.setAll(load.getValue());
                historyTable.sort();
                historyPlaceholder.setText("No transactions recorded for this customer.");
            } catch (AccessDeniedException denied) {
                showAccessDenied(denied);
            }
        });
        load.setOnFailed(event -> {
            if (!active || historyLoad != load) {
                return;
            }
            historyLoad = null;
            if (load.getException() instanceof AccessDeniedException denied) {
                showAccessDenied(denied);
            } else {
                historyPlaceholder.setText("Cannot load history. Please try again.");
                statusLabel.setText("Could not load customer history. Select Refresh to retry.");
            }
        });
        start(load, "tindatrack-load-credit-history");
    }

    private void clearHistory() {
        history.clear();
        historyTitle.setText("Customer history");
        historyPlaceholder.setText("Select a customer to view utang and payments.");
    }

    private void showAccessDenied(AccessDeniedException denied) {
        cancelLoads();
        customers.clear();
        clearHistory();
        customerPlaceholder.setText(denied.getMessage());
        statusLabel.setText(denied.getMessage());
        setLoading(true);
    }

    private void setLoading(boolean loading) {
        searchField.setDisable(loading);
        searchButton.setDisable(loading);
        refreshButton.setDisable(loading);
        recordUtangButton.setDisable(loading);
        customersTable.setDisable(loading);
    }

    private void cancelHistory() {
        if (historyLoad != null) {
            historyLoad.cancel();
            historyLoad = null;
        }
    }

    private void cancelLoads() {
        if (paymentCustomerLoad != null) {
            paymentCustomerLoad.cancel();
            paymentCustomerLoad = null;
        }
        if (customerLoad != null) {
            customerLoad.cancel();
            customerLoad = null;
        }
        cancelHistory();
    }

    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private static <T> void formatMoney(TableColumn<T, BigDecimal> column) {
        column.setCellFactory(value -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : "₱" + amount.setScale(2).toPlainString());
            }
        });
    }

    private static <T> void formatDate(TableColumn<T, LocalDateTime> column) {
        column.setCellFactory(value -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty ? null : date == null ? "—" : DATE_FORMAT.format(date));
            }
        });
    }
}
