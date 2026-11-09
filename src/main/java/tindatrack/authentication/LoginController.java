package tindatrack.authentication;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Handles input and feedback on the login screen. */
public class LoginController {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label feedbackLabel;

    @FXML
    private void handleLogin() {
        if (usernameField.getText().isBlank()) {
            showFeedback("Please fill in all fields.", true);
            usernameField.requestFocus();
            return;
        }

        if (passwordField.getText().isBlank()) {
            showFeedback("Please fill in all fields.", true);
            passwordField.requestFocus();
            return;
        }

        passwordField.clear();
        showFeedback("Sign-in is coming soon.", false);
    }

    @FXML
    private void handleCreateAccount() {
        showFeedback("Account creation is coming soon.", false);
    }

    @FXML
    private void handleGuestAccess() {
        passwordField.clear();
        showFeedback("Guest access is coming soon.", false);
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.pseudoClassStateChanged(ERROR, error);
    }
}
