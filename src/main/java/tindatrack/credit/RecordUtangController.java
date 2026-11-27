package tindatrack.credit;

import java.time.LocalDate;
import java.util.List;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import tindatrack.core.SessionState.AccessDeniedException;

/** Collects SB08 input and saves through the credit service on a background task. */
public final class RecordUtangController {

    @FXML private VBox fields;
    @FXML private ComboBox<Customer> customerField;
    @FXML private CheckBox newCustomer;
    @FXML private VBox newCustomerFields;
    @FXML private TextField customerNameField;
    @FXML private TextArea itemsField;
    @FXML private TextField amountField;
    @FXML private DatePicker dateField;
    @FXML private Label customerError;
    @FXML private Label itemsError;
    @FXML private Label amountError;
    @FXML private Label dateError;
    @FXML private Label formError;
    @FXML private Button retryButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private CreditService service;
    private Customer initialCustomer;
    private Task<List<Customer>> customerLoad;
    private boolean saving;
    private Integer savedCustomerId;

    @FXML
    private void initialize() {
        dateField.setValue(LocalDate.now());
        newCustomerFields.visibleProperty().bind(newCustomer.selectedProperty());
        newCustomerFields.managedProperty().bind(newCustomer.selectedProperty());
        customerField.disableProperty().bind(newCustomer.selectedProperty());
        customerField.setConverter(new StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                // IDs distinguish existing records that happen to have the same name.
                return customer == null ? "" : customer.name() + " (ID: " + customer.id() + ")";
            }

            @Override
            public Customer fromString(String text) {
                return null; // This selector is not editable; new names use their own field.
            }
        });
    }

    public void configure(CreditService service, Customer initialCustomer) {
        this.service = service;
        this.initialCustomer = initialCustomer;
        service.requireOwnerAccess();
        loadCustomers();
    }

    @FXML
    private void loadCustomers() {
        if (customerLoad != null || saving) {
            return;
        }
        fields.setDisable(true);
        saveButton.setDisable(true);
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        showError(formError, "Loading customers...");
        Task<List<Customer>> load = new Task<>() {
            @Override
            protected List<Customer> call() throws Exception {
                return service.findCustomers();
            }
        };
        customerLoad = load;
        load.setOnSucceeded(event -> {
            if (customerLoad != load) {
                return;
            }
            customerLoad = null;
            try {
                service.requireOwnerAccess();
                customerField.getItems().setAll(load.getValue());
                if (initialCustomer != null) {
                    customerField.getItems().stream().filter(c -> c.id() == initialCustomer.id())
                            .findFirst().ifPresent(customerField::setValue);
                }
                newCustomer.setSelected(load.getValue().isEmpty());
                fields.setDisable(false);
                saveButton.setDisable(false);
                showError(formError, "");
            } catch (AccessDeniedException denied) {
                showError(formError, denied.getMessage());
            }
        });
        load.setOnFailed(event -> {
            if (customerLoad != load) {
                return;
            }
            customerLoad = null;
            if (load.getException() instanceof AccessDeniedException denied) {
                showError(formError, denied.getMessage());
            } else {
                showError(formError, "Cannot load customers. Please try again.");
                retryButton.setVisible(true);
                retryButton.setManaged(true);
            }
        });
        start(load, "tindatrack-utang-customers");
    }

    @FXML
    private void handleSave() {
        if (saving || savedCustomerId != null || saveButton.isDisabled()) {
            return;
        }
        clearErrors();
        Customer selected = newCustomer.isSelected() ? null : customerField.getValue();
        String name = newCustomer.isSelected() ? customerNameField.getText()
                : selected == null ? "" : selected.name();
        UtangRequest request;
        try {
            request = service.prepareUtang(selected == null ? null : selected.id(), name,
                    itemsField.getText(), amountField.getText(), dateField.getValue());
        } catch (CreditRules.ValidationException invalid) {
            showValidationError(invalid);
            return;
        } catch (AccessDeniedException denied) {
            showError(formError, denied.getMessage());
            return;
        }
        setSaving(true);
        Task<Integer> save = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return service.recordUtang(request);
            }
        };
        save.setOnSucceeded(event -> {
            savedCustomerId = save.getValue();
            setSaving(false);
            closeForm();
        });
        save.setOnFailed(event -> {
            setSaving(false);
            if (save.getException() instanceof CreditRules.ValidationException invalid) {
                showValidationError(invalid);
            } else {
                String message = save.getException() instanceof AccessDeniedException denied
                        ? denied.getMessage() : "Utang was not saved. Please try again.";
                showError(formError, message);
            }
        });
        start(save, "tindatrack-save-utang");
    }

    private void showValidationError(CreditRules.ValidationException invalid) {
        Label target = switch (invalid.field()) {
            case "customer" -> customerError;
            case "amount" -> amountError;
            case "items" -> itemsError;
            case "date" -> dateError;
            default -> formError;
        };
        showError(target, invalid.getMessage());
    }

    private void clearErrors() {
        for (Label label : List.of(customerError, itemsError, amountError, dateError, formError)) {
            showError(label, "");
        }
    }

    private void showError(Label label, String text) {
        label.setText(text);
        label.setVisible(!text.isEmpty());
        label.setManaged(label.isVisible());
    }

    private void setSaving(boolean saving) {
        this.saving = saving;
        fields.setDisable(saving);
        saveButton.setDisable(saving);
        cancelButton.setDisable(saving);
        saveButton.setText(saving ? "Saving..." : "Save");
    }

    boolean isSaving() {
        return saving;
    }

    Integer savedCustomerId() {
        return savedCustomerId;
    }

    @FXML
    private void closeForm() {
        if (!saving) {
            if (customerLoad != null) {
                customerLoad.cancel();
                customerLoad = null;
            }
            saveButton.getScene().getWindow().hide();
        }
    }

    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }
}
