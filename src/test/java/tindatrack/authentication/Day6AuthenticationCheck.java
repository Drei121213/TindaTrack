package tindatrack.authentication;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import tindatrack.core.PasswordHasher;
import tindatrack.core.SessionState;

/** Real FXML/controller checks with fake DAO responses. No database is opened. */
public final class Day6AuthenticationCheck {
    private static Stage stage;
    private static Object controller;

    public static void main(String[] args) throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> { stage = new Stage(); stage.setOpacity(0); return null; });
            run("Login_RequiredFields", () -> {
                FakeUsers users = login();
                invoke("handleLogin");
                require(text("feedbackLabel").equals("Please fill in all fields."), "Missing required-field feedback");
                require(users.reads.get() == 0, "Blank login reached the DAO");
            });
            run("Login_BusyGuard", () -> {
                FakeUsers users = login(); users.readGate = new CountDownLatch(1);
                fillLogin(); invoke("handleLogin");
                try {
                    waitFor(() -> users.reads.get() == 1);
                    invoke("handleLogin"); invoke("handleGuestAccess");
                    require(text("feedbackLabel").equals("Signing in..."), "Repeated action replaced progress");
                    require(users.guests.get() == 0 && users.reads.get() == 1, "Busy login accepted another action");
                } finally { users.readGate.countDown(); }
                waitFor(() -> field("signInTask") == null);
            });
            run("Login_ObsoleteResult", () -> {
                FakeUsers users = login();
                users.account = Optional.of(new UserDao.LoginAccount(1, "Test Owner", PasswordHasher.hash("store123".toCharArray())));
                users.readGate = new CountDownLatch(1);
                fillLogin(); invoke("handleLogin");
                Task<?> pending = fx(() -> (Task<?>) field("signInTask"));
                try {
                    waitFor(() -> users.reads.get() == 1);
                    fx(() -> { ((LoginController) controller).deactivate(); return null; });
                } finally { users.readGate.countDown(); }
                waitFor(() -> pending.getState() == javafx.concurrent.Worker.State.SUCCEEDED);
                require(users.signedIn.get() == 0, "An inactive screen delivered a successful login");
            });
            run("Login_ConnectionRetry", () -> {
                FakeUsers users = login(); users.readFailure = true;
                fillLogin(); invoke("handleLogin"); waitFor(() -> field("signInTask") == null);
                require(text("feedbackLabel").equals("Cannot connect to the database. Please try again."), "Technical login error leaked");
                users.readFailure = false;
                fillLogin(); invoke("handleLogin"); waitFor(() -> field("signInTask") == null);
                require(text("feedbackLabel").equals("Invalid username or password."), "Retry did not recover");
            });
            run("Login_AccountCheckRetry", () -> {
                FakeUsers users = login(); users.checkFailure = true;
                invoke("checkRegistrationAvailability"); waitFor(() -> field("accountCheck") == null);
                require(fx(() -> ((Hyperlink) node("createAccountLink")).getText()).equals("Retry account check"), "Missing account-check retry");
                users.checkFailure = false;
                invoke("handleCreateAccount"); waitFor(() -> field("accountCheck") == null);
                require(!fx(() -> node("createAccountLink").isVisible()), "Existing owner exposed registration");
            });
            run("Registration_RequiredFields", () -> {
                FakeUsers users = registration(); invoke("handleCreateAccount");
                require(!text("fullNameError").isEmpty() && !text("passwordError").isEmpty(), "Blank registration lacked errors");
                fillRegistration(); setText("confirmationField", "different"); invoke("handleCreateAccount");
                require(text("confirmationError").equals("Passwords do not match."), "Mismatch was accepted");
                require(users.writes.get() == 0, "Invalid registration wrote data");
            });
            run("Registration_BusyAndDuplicate", () -> {
                FakeUsers users = registration(); users.writeGate = new CountDownLatch(1);
                fillRegistration(); invoke("handleCreateAccount");
                try {
                    waitFor(() -> users.writes.get() == 1);
                    invoke("handleCreateAccount"); invoke("closeForm");
                    require(users.writes.get() == 1 && fx(stage::isShowing), "Repeated submission or close bypassed busy state");
                } finally { users.writeGate.countDown(); }
                waitFor(() -> !((RegistrationController) controller).isSaving());
                require(text("usernameError").equals("That username is already in use."), "Duplicate username feedback differed");
                users.createResult = UserDao.CreateResult.OWNER_EXISTS;
                fillRegistration(); invoke("handleCreateAccount");
                waitFor(() -> !((RegistrationController) controller).isSaving());
                require(text("formError").equals("An owner account already exists."), "Existing owner feedback differed");
            });
            run("Registration_ConnectionRetry", () -> {
                FakeUsers users = registration(); users.writeFailure = true;
                fillRegistration(); invoke("handleCreateAccount");
                waitFor(() -> !((RegistrationController) controller).isSaving());
                require(text("formError").equals("Account was not created. Please try again."), "Technical registration error leaked");
                users.writeFailure = false; users.createResult = UserDao.CreateResult.CREATED;
                fillRegistration(); invoke("handleCreateAccount"); waitFor(() -> !stage.isShowing());
                int saved = users.writes.get();
                invoke("handleCreateAccount");
                require(users.writes.get() == saved, "Completed registration submitted again");
            });
        } finally {
            fx(() -> { if (controller instanceof LoginController login) login.deactivate(); stage.close(); return null; });
            Platform.exit();
        }
    }

    private static FakeUsers login() throws Exception {
        load("login");
        FakeUsers users = new FakeUsers();
        fx(() -> {
            assign("users", users);
            assign("onAuthenticated", (Consumer<OwnerSession>) owner -> users.signedIn.incrementAndGet());
            assign("onGuestAccess", (Runnable) () -> users.guests.incrementAndGet());
            return null;
        });
        return users;
    }

    private static FakeUsers registration() throws Exception {
        load("register"); FakeUsers users = new FakeUsers();
        fx(() -> { ((RegistrationController) controller).setUserDao(users); return null; });
        return users;
    }

    private static void load(String name) throws Exception {
        fx(() -> {
            if (controller instanceof LoginController login) login.deactivate();
            FXMLLoader loader = new FXMLLoader(Day6AuthenticationCheck.class.getResource("/tindatrack/fxml/" + name + ".fxml"));
            Scene scene = new Scene(loader.load(), 500, 600);
            scene.getStylesheets().add(Day6AuthenticationCheck.class.getResource("/tindatrack/css/login.css").toExternalForm());
            controller = loader.getController(); stage.setScene(scene); stage.show(); return null;
        });
    }

    private static void fillLogin() throws Exception { setText("usernameField", "owner"); setText("passwordField", "store123"); }
    private static void fillRegistration() throws Exception {
        setText("fullNameField", "Test Owner"); setText("usernameField", "owner");
        setText("passwordField", "store123"); setText("confirmationField", "store123");
    }
    private static javafx.scene.Node node(String id) { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); return stage.getScene().lookup("#" + id); }
    private static String text(String id) throws Exception { return fx(() -> ((Label) node(id)).getText()); }
    private static void setText(String id, String value) throws Exception { fx(() -> { ((TextInputControl) node(id)).setText(value); return null; }); }
    private static Object field(String name) throws Exception { Field f = controller.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(controller); }
    private static void assign(String name, Object value) throws Exception { Field f = controller.getClass().getDeclaredField(name); f.setAccessible(true); f.set(controller, value); }
    private static void invoke(String name) throws Exception {
        fx(() -> { Method method = controller.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(controller); return null; });
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable failure) { future.completeExceptionally(failure); } });
        return future.get(12, TimeUnit.SECONDS);
    }
    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < end) { if (fx(condition)) return; Thread.sleep(25); }
        throw new AssertionError("Timed out waiting for controller");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void run(String id, CheckedAction action) throws Exception {
        try { action.run(); System.out.println("AUTH\t" + id + "\tPass\tFXML/controller assertions passed with fake DAO responses; no database opened."); }
        catch (Exception | AssertionError failure) { System.out.println("AUTH\t" + id + "\tFailed\t" + failure); throw failure; }
    }
    private interface CheckedAction { void run() throws Exception; }

    private static final class FakeUsers extends UserDao {
        final AtomicInteger reads = new AtomicInteger(), writes = new AtomicInteger();
        final AtomicInteger signedIn = new AtomicInteger(), guests = new AtomicInteger();
        volatile CountDownLatch readGate, writeGate;
        volatile boolean readFailure, writeFailure, checkFailure;
        volatile Optional<LoginAccount> account = Optional.empty();
        volatile CreateResult createResult = CreateResult.USERNAME_TAKEN;
        FakeUsers() { super(new SessionState()); }
        @Override Optional<LoginAccount> findByUsername(String name) throws SQLException {
            reads.incrementAndGet(); await(readGate);
            if (readFailure) throw new SQLException("Technical connection details must stay private.");
            return account;
        }
        @Override public boolean hasOwner() throws SQLException {
            if (checkFailure) throw new SQLException("Technical connection details must stay private.");
            return true;
        }
        @Override public CreateResult createOwner(String name, String username, String hash) throws SQLException {
            writes.incrementAndGet(); await(writeGate);
            if (writeFailure) throw new SQLException("Technical connection details must stay private.");
            return createResult;
        }
        private static void await(CountDownLatch gate) throws SQLException {
            try { if (gate != null && !gate.await(8, TimeUnit.SECONDS)) throw new SQLException("Test gate timed out"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new SQLException("Interrupted", interrupted); }
        }
    }
}
