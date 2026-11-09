package tindatrack.authentication;

import java.util.Arrays;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import tindatrack.core.PasswordHasher;
import tindatrack.core.SessionState.AccessDeniedException;

/** Validates the owner registration form and displays the result of saving it. */
public class RegistrationController {

    @FXML private VBox formFields;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmationField;
    @FXML private Label fullNameError;
    @FXML private Label usernameError;
    @FXML private Label passwordError;
    @FXML private Label confirmationError;
    @FXML private Label formError;
    @FXML private Label saveStatus;
    @FXML private Button createButton;
    @FXML private Button cancelButton;

    private String createdUsername;
    private UserDao users;

    void setUserDao(UserDao users) {
        this.users = users;
    }

    @FXML
    private void handleCreateAccount() {
        String fullName = fullNameField.getText().strip();
        String username = usernameField.getText().strip();
        if (!validate(fullName, username)) {
            return;
        }

        char[] password = passwordField.getText().toCharArray();
        passwordField.clear();
        confirmationField.clear();
        setSaving(true);

        Task<UserDao.CreateResult> save = new Task<>() {
            @Override
            protected UserDao.CreateResult call() throws Exception {
                try {
                    String passwordHash = PasswordHasher.hash(password);
                    return users.createOwner(fullName, username, passwordHash);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };

        save.setOnSucceeded(event -> {
            setSaving(false);
            switch (save.getValue()) {
                case CREATED -> {
                    createdUsername = username;
                    closeForm();
                }
                case USERNAME_TAKEN -> {
                    showError(usernameError, "That username is already in use.");
                    usernameField.requestFocus();
                }
                case OWNER_EXISTS -> showError(formError, "An owner account already exists.");
            }
        });
        save.setOnFailed(event -> {
            setSaving(false);
            String message = save.getException() instanceof AccessDeniedException denied
                    ? denied.getMessage() : "Account was not created. Please try again.";
            showError(formError, message);
        });

        Thread worker = new Thread(save, "tindatrack-create-owner");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean validate(String fullName, String username) {
        showError(formError, "");
        String nameMessage = requiredTextError(fullName, "Full name", 100);
        String usernameMessage = requiredTextError(username, "Username", 50);

        String password = passwordField.getText();
        String confirmation = confirmationField.getText();
        String passwordMessage = "";
        if (password.isBlank()) {
            passwordMessage = "Enter a password.";
        } else if (password.codePointCount(0, password.length()) < 6) {
            passwordMessage = "Password must be at least 6 characters.";
        }

        String confirmationMessage = "";
        if (confirmation.isBlank()) {
            confirmationMessage = "Confirm your password.";
        } else if (!password.isBlank() && !password.equals(confirmation)) {
            confirmationMessage = "Passwords do not match.";
        }

        showError(fullNameError, nameMessage);
        showError(usernameError, usernameMessage);
        showError(passwordError, passwordMessage);
        showError(confirmationError, confirmationMessage);
        return nameMessage.isEmpty() && usernameMessage.isEmpty()
                && passwordMessage.isEmpty() && confirmationMessage.isEmpty();
    }

    private String requiredTextError(String value, String fieldName, int maxLength) {
        if (value.isBlank()) {
            return fieldName + " is required.";
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            return fieldName + " must be " + maxLength + " characters or fewer.";
        }
        return "";
    }

    private void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(!message.isEmpty());
        label.setManaged(label.isVisible());
    }

    private void setSaving(boolean saving) {
        formFields.setDisable(saving);
        createButton.setDisable(saving);
        cancelButton.setDisable(saving);
        saveStatus.setVisible(saving);
        saveStatus.setManaged(saving);
    }

    @FXML
    private void closeForm() {
        passwordField.clear();
        confirmationField.clear();
        cancelButton.getScene().getWindow().hide();
    }

    boolean isSaving() {
        return createButton.isDisabled();
    }

    String getCreatedUsername() {
        return createdUsername;
    }
}
