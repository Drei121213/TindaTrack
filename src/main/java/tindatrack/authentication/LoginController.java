package tindatrack.authentication;

import java.io.IOException;

import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
    private Hyperlink createAccountLink;

    private boolean registrationAvailable;

    @FXML
    private void initialize() {
        createAccountLink.managedProperty().bind(createAccountLink.visibleProperty());
        checkRegistrationAvailability();
    }

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
        if (!registrationAvailable) {
            showFeedback("", false);
            checkRegistrationAvailability();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tindatrack/fxml/register.fxml"));
            Scene scene = new Scene(loader.load(), 500, 600);
            scene.getStylesheets().addAll(createAccountLink.getScene().getStylesheets());
            RegistrationController controller = loader.getController();

            Stage dialog = new Stage();
            dialog.initOwner(createAccountLink.getScene().getWindow());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("TindaTrack - Create Owner Account");
            dialog.setScene(scene);
            dialog.setMinWidth(440);
            dialog.setMinHeight(460);
            dialog.setOnCloseRequest(event -> {
                if (controller.isSaving()) {
                    event.consume();
                }
            });
            dialog.showAndWait();

            String createdUsername = controller.getCreatedUsername();
            if (createdUsername != null) {
                registrationAvailable = false;
                createAccountLink.setVisible(false);
                usernameField.setText(createdUsername);
                passwordField.clear();
                showFeedback("Account created. You can now log in.", false);
                passwordField.requestFocus();
            } else {
                checkRegistrationAvailability();
            }
        } catch (IOException exception) {
            showFeedback("Could not open the account form. Please try again.", true);
        }
    }

    private void checkRegistrationAvailability() {
        createAccountLink.setVisible(false);
        registrationAvailable = false;

        Task<Boolean> check = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return new UserDao().hasOwner();
            }
        };
        check.setOnSucceeded(event -> {
            registrationAvailable = !check.getValue();
            createAccountLink.setText("Create Account");
            createAccountLink.setVisible(registrationAvailable);
        });
        check.setOnFailed(event -> {
            createAccountLink.setText("Retry account check");
            createAccountLink.setVisible(true);
            showFeedback("Could not check account availability. Please try again.", true);
        });

        Thread worker = new Thread(check, "tindatrack-check-owner");
        worker.setDaemon(true);
        worker.start();
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
