package tindatrack.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Callable;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.converter.LocalDateStringConverter;
import tindatrack.core.SessionState.AccessDeniedException;

/** Validates the add and edit forms and saves through the owner product DAO. */
public final class ProductFormController {

    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    @FXML private VBox fields;
    @FXML private Label formTitle;
    @FXML private TextField nameField;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private DatePicker expirationField;
    @FXML private CheckBox noExpiration;
    @FXML private Label nameError;
    @FXML private Label priceError;
    @FXML private Label stockError;
    @FXML private Label dateError;
    @FXML private Label formError;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private ProductDao products;
    private Product original;
    private boolean saved;

    @FXML
    private void initialize() {
        DateTimeFormatter format = DateTimeFormatter.ISO_LOCAL_DATE;
        expirationField.setConverter(new LocalDateStringConverter(format, format));
        expirationField.disableProperty().bind(noExpiration.selectedProperty());
        noExpiration.selectedProperty().addListener((property, previous, selected) -> {
            if (selected) {
                expirationField.setValue(null);
                showError(dateError, "");
            }
        });
    }

    void configure(ProductDao products, Product original) {
        this.products = products;
        this.original = original;
        if (original != null) {
            formTitle.setText("Edit Product");
            nameField.setText(original.name());
            nameField.setEditable(false);
            priceField.setText(original.price().toPlainString());
            stockField.setText(Integer.toString(original.stockQuantity()));
            expirationField.setValue(original.expirationDate());
            noExpiration.setSelected(original.expirationDate() == null);
        }
    }

    @FXML
    private void handleSave() {
        if (isSaving()) {
            return;
        }
        showError(formError, "");
        String name = nameField.getText().strip();
        String nameMessage = name.isEmpty() ? "Enter a product name." : "";
        if (name.codePointCount(0, name.length()) > 150) {
            nameMessage = "Use 150 characters or fewer.";
        }
        showError(nameError, nameMessage);

        BigDecimal price = readPrice();
        Integer stock = readStock();
        LocalDate expiry = expirationField.getValue();
        boolean validDate = expiry == null || (expiry.getYear() >= 1000 && expiry.getYear() <= 9999);
        showError(dateError, validDate ? "" : "Please enter a valid date.");
        if (!nameMessage.isEmpty() || price == null || stock == null || !validDate) {
            return;
        }
        boolean expirationChanged = original == null || !Objects.equals(expiry, original.expirationDate());
        if (original != null && price.compareTo(original.price()) == 0
                && stock == original.stockQuantity() && !expirationChanged) {
            showError(formError, "No changes to save.");
            return;
        }
        if (expirationChanged && expiry != null && expiry.isBefore(LocalDate.now()) && !confirmPastDate()) {
            return;
        }
        if (original == null) {
            save(() -> products.addProduct(name, price, stock, expiry) == ProductDao.AddResult.ADDED,
                    nameError, "This product already exists.", "Product was not saved. Please try again.");
        } else {
            save(() -> products.updateProduct(original, price, stock, expiry), formError,
                    "This product changed or was removed. Cancel and refresh Inventory before editing again.",
                    "Changes were not saved. Please try again.");
        }
    }

    private BigDecimal readPrice() {
        String text = priceField.getText().strip();
        showError(priceError, "");
        if (text.isEmpty()) {
            showError(priceError, "Enter a price.");
            return null;
        }
        if (text.matches("(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})")) {
            BigDecimal price = new BigDecimal(text);
            if (price.signum() > 0 && price.compareTo(MAX_PRICE) <= 0) {
                return price;
            }
        }
        showError(priceError, "Enter a price from 0.01 to 99999999.99 with up to two decimal places.");
        return null;
    }

    private Integer readStock() {
        String text = stockField.getText().strip();
        showError(stockError, "");
        if (text.isEmpty()) {
            showError(stockError, "Enter a stock quantity.");
            return null;
        }
        if (text.matches("[0-9]+")) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException exception) {
                // The number is larger than the stock column can store.
            }
        }
        showError(stockError, "Enter a whole number from 0 to 2147483647.");
        return null;
    }

    private boolean confirmPastDate() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "This date has already passed. Save anyway?", ButtonType.YES, ButtonType.NO);
        confirmation.initOwner(saveButton.getScene().getWindow());
        confirmation.setTitle("Past expiration date");
        confirmation.setHeaderText(null);
        return confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void save(Callable<Boolean> write, Label conflictLabel, String conflictMessage,
                      String failureMessage) {
        setSaving(true);
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return write.call();
            }
        };
        task.setOnSucceeded(event -> {
            setSaving(false);
            if (task.getValue()) {
                saved = true;
                closeForm();
            } else {
                showError(conflictLabel, conflictMessage);
                if (conflictLabel == nameError) {
                    nameField.requestFocus();
                }
            }
        });
        task.setOnFailed(event -> {
            setSaving(false);
            String message = task.getException() instanceof AccessDeniedException denied
                    ? denied.getMessage() : failureMessage;
            showError(formError, message);
        });

        Thread worker = new Thread(task, "tindatrack-save-product");
        worker.setDaemon(true);
        worker.start();
    }

    private void showError(Label label, String text) {
        label.setText(text);
        label.setVisible(!text.isEmpty());
        label.setManaged(label.isVisible());
    }

    private void setSaving(boolean saving) {
        fields.setDisable(saving);
        saveButton.setDisable(saving);
        cancelButton.setDisable(saving);
        saveButton.setText(saving ? "Saving..." : "Save");
    }

    boolean isSaving() {
        return saveButton.isDisabled();
    }

    boolean wasSaved() {
        return saved;
    }

    @FXML
    private void closeForm() {
        if (!isSaving()) {
            saveButton.getScene().getWindow().hide();
        }
    }
}
