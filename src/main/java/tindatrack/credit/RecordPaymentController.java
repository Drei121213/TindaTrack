package tindatrack.credit;

import java.math.BigDecimal;
import java.time.LocalDate;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import tindatrack.core.SessionState.AccessDeniedException;

/** Shows SB09 payment input, a balance preview, and safe save feedback. */
public final class RecordPaymentController {

    @FXML private VBox fields;
    @FXML private Label customerLabel;
    @FXML private Label balanceLabel;
    @FXML private TextField amountField;
    @FXML private DatePicker dateField;
    @FXML private Label newBalanceLabel;
    @FXML private Label amountError;
    @FXML private Label dateError;
    @FXML private Label formError;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private CreditService service;
    private Customer customer;
    private BigDecimal balance;
    private BigDecimal savedBalance;
    private boolean saving;

    @FXML
    private void initialize() {
        dateField.setValue(LocalDate.now());
        amountField.textProperty().addListener((property, previous, current) -> updatePreview());
    }

    public void configure(CreditService service, Customer customer) {
        service.requireOwnerAccess();
        CreditRules.requireOutstandingBalance(customer.balance());
        this.service = service;
        this.customer = customer;
        this.balance = customer.balance();
        customerLabel.setText("Customer: " + customer.name());
        updateBalanceLabel();
        updatePreview();
    }

    private void updatePreview() {
        if (service == null) {
            return;
        }
        showError(amountError, "");
        if (amountField.getText().isBlank()) {
            newBalanceLabel.setText("New Balance: —");
            return;
        }
        try {
            BigDecimal remaining = service.previewPayment(balance, amountField.getText());
            newBalanceLabel.setText("New Balance: ₱" + remaining.toPlainString());
        } catch (CreditRules.ValidationException invalid) {
            newBalanceLabel.setText("New Balance: —");
            showError(amountError, invalid.getMessage());
        } catch (AccessDeniedException denied) {
            newBalanceLabel.setText("New Balance: —");
            showError(formError, denied.getMessage());
            saveButton.setDisable(true);
        }
    }

    @FXML
    private void handleSave() {
        if (saving || savedBalance != null || saveButton.isDisabled()) {
            return;
        }
        showError(amountError, "");
        showError(dateError, "");
        showError(formError, "");
        PaymentRequest request;
        try {
            request = service.preparePayment(customer.id(), amountField.getText(), dateField.getValue());
            service.previewPayment(balance, amountField.getText());
        } catch (CreditRules.ValidationException invalid) {
            showValidationError(invalid);
            return;
        } catch (AccessDeniedException denied) {
            showError(formError, denied.getMessage());
            return;
        }
        setSaving(true);
        Task<BigDecimal> save = new Task<>() {
            @Override
            protected BigDecimal call() throws Exception {
                return service.recordPayment(request);
            }
        };
        save.setOnSucceeded(event -> {
            savedBalance = save.getValue();
            setSaving(false);
            closeForm();
        });
        save.setOnFailed(event -> {
            setSaving(false);
            if (save.getException() instanceof CreditRules.PaymentBalanceException changed) {
                // Another payment may have changed the balance since this form opened.
                balance = changed.balance();
                updateBalanceLabel();
                updatePreview();
                showValidationError(changed);
                saveButton.setDisable(balance.signum() <= 0);
            } else if (save.getException() instanceof CreditRules.ValidationException invalid) {
                showValidationError(invalid);
            } else {
                String message = save.getException() instanceof AccessDeniedException denied
                        ? denied.getMessage() : "Payment was not saved. Please try again.";
                showError(formError, message);
            }
        });
        Thread worker = new Thread(save, "tindatrack-save-payment");
        worker.setDaemon(true);
        worker.start();
    }

    private void updateBalanceLabel() {
        balanceLabel.setText("Current Balance: ₱" + balance.setScale(2).toPlainString());
    }

    private void showValidationError(CreditRules.ValidationException invalid) {
        Label target = switch (invalid.field()) {
            case "amount" -> amountError;
            case "date" -> dateError;
            default -> formError;
        };
        showError(target, invalid.getMessage());
    }

    private void showError(Label label, String text) {
        label.setText(text);
        label.setVisible(!text.isEmpty());
        label.setManaged(label.isVisible());
    }

    private void setSaving(boolean saving) {
        this.saving = saving;
        fields.setDisable(saving);
        saveButton.setDisable(saving || balance.signum() <= 0);
        cancelButton.setDisable(saving);
        saveButton.setText(saving ? "Saving..." : "Save");
    }

    boolean isSaving() {
        return saving;
    }

    BigDecimal savedBalance() {
        return savedBalance;
    }

    @FXML
    private void closeForm() {
        if (!saving) {
            saveButton.getScene().getWindow().hide();
        }
    }
}
