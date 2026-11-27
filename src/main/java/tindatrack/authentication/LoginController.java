package tindatrack.authentication;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tindatrack.core.SessionState;
import tindatrack.core.SessionState.AccessDeniedException;

/** Handles input and feedback on the login screen. */
public class LoginController {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    @FXML
    private VBox loginForm;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label feedbackLabel;

    @FXML
    private Hyperlink createAccountLink;

    private boolean registrationAvailable;
    private boolean active = true;
    private Task<?> signInTask;
    private Task<Boolean> accountCheck;
    private UserDao users;
    private Consumer<OwnerSession> onAuthenticated;
    private Runnable onGuestAccess;

    public void configure(SessionState sessions, Consumer<OwnerSession> onAuthenticated,
                          Runnable onGuestAccess) {
        users = new UserDao(sessions);
        this.onAuthenticated = onAuthenticated;
        this.onGuestAccess = onGuestAccess;
        checkRegistrationAvailability();
    }

    public void showScreenError(String screenName) {
        showFeedback("Could not open " + screenName + ". Please try again.", true);
    }

    @FXML
    private void initialize() {
        createAccountLink.managedProperty().bind(createAccountLink.visibleProperty());
    }

    /**
     * Validates login input, then checks credentials on a background task.
     * Clears the password field before starting and clears the copied character array
     * when the task finishes its credential check, including exceptional exits.
     */
    @FXML
    private void handleLogin() {
        if (!active || signInTask != null) return;
        String username = usernameField.getText().strip();
        if (username.isEmpty()) {
            showFeedback("Please fill in all fields.", true);
            usernameField.requestFocus();
            return;
        }

        if (passwordField.getText().isBlank()) {
            showFeedback("Please fill in all fields.", true);
            passwordField.requestFocus();
            return;
        }

        char[] password = passwordField.getText().toCharArray();
        passwordField.clear();
        loginForm.setDisable(true);
        showFeedback("Signing in...", false);

        Task<Optional<OwnerSession>> signIn = new Task<>() {
            @Override
            protected Optional<OwnerSession> call() throws Exception {
                try {
                    return new AuthenticationService(users).authenticate(username, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };
        signIn.setOnSucceeded(event -> {
            if (!active || signInTask != signIn) return;
            signInTask = null;
            loginForm.setDisable(false);
            Optional<OwnerSession> owner = signIn.getValue();
            if (owner.isPresent()) {
                onAuthenticated.accept(owner.get());
            } else {
                showFeedback("Invalid username or password.", true);
                passwordField.requestFocus();
            }
        });
        signIn.setOnFailed(event -> {
            if (!active || signInTask != signIn) return;
            signInTask = null;
            loginForm.setDisable(false);
            Throwable failure = signIn.getException();
            if (failure instanceof AccessDeniedException denied) {
                showFeedback(denied.getMessage(), true);
            } else if (failure instanceof IOException || failure instanceof SQLException) {
                showFeedback("Cannot connect to the database. Please try again.", true);
            } else {
                showFeedback("Could not sign in. Please try again.", true);
            }
            passwordField.requestFocus();
        });

        signInTask = signIn;
        Thread worker = new Thread(signIn, "tindatrack-sign-in");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleCreateAccount() {
        if (!active || signInTask != null || accountCheck != null) return;
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
            controller.setUserDao(users);

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

    /**
     * Checks whether the single owner account already exists without blocking JavaFX.
     * Keeps registration unavailable while checking; a failed check exposes a retry link.
     */
    private void checkRegistrationAvailability() {
        if (!active || accountCheck != null) return;
        createAccountLink.setVisible(false);
        registrationAvailable = false;

        Task<Boolean> check = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return users.hasOwner();
            }
        };
        check.setOnSucceeded(event -> {
            if (!active || accountCheck != check) return;
            accountCheck = null;
            registrationAvailable = !check.getValue();
            createAccountLink.setText("Create Account");
            createAccountLink.setVisible(registrationAvailable);
        });
        check.setOnFailed(event -> {
            if (!active || accountCheck != check) return;
            accountCheck = null;
            createAccountLink.setText("Retry account check");
            createAccountLink.setVisible(true);
            if (feedbackLabel.getText().isEmpty()) {
                showFeedback("Could not check account availability. Please try again.", true);
            }
        });

        accountCheck = check;
        Thread worker = new Thread(check, "tindatrack-check-owner");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleGuestAccess() {
        if (!active || signInTask != null) return;
        passwordField.clear();
        onGuestAccess.run();
    }

    /** Prevents an old screen's asynchronous results from changing the active screen. */
    public void deactivate() {
        active = false;
        passwordField.clear();
        if (accountCheck != null) {
            accountCheck.cancel();
            accountCheck = null;
        }
        // Let credential verification finish so its finally block clears the password copy.
        // Its callbacks are ignored because this controller is no longer active.
        signInTask = null;
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.pseudoClassStateChanged(ERROR, error);
    }
}
