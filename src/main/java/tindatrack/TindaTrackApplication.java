package tindatrack;

import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import tindatrack.authentication.LoginController;
import tindatrack.authentication.OwnerSession;
import tindatrack.core.OwnerHomeController;
import tindatrack.core.OwnerNavigationController;
import tindatrack.core.OwnerNavigationController.Destination;
import tindatrack.core.SessionState;
import tindatrack.core.SessionState.AccessDeniedException;
import tindatrack.credit.CreditController;
import tindatrack.credit.CreditDao;
import tindatrack.guest.GuestController;
import tindatrack.guest.GuestService;
import tindatrack.guest.GuestSession;
import tindatrack.inventory.ExpiringSoonController;
import tindatrack.inventory.InventoryController;
import tindatrack.inventory.ProductDao;

/** One main window, with owner views reused only during the current login. */
public class TindaTrackApplication extends Application {

    private final SessionState sessions = new SessionState();
    private BorderPane ownerLayout;
    private OwnerNavigationController navigation;
    private Destination currentDestination;
    private Runnable leaveCurrentView = () -> { };
    private ProductDao products;
    private CreditDao credits;
    private Parent homeView;
    private Parent inventoryView;
    private InventoryController inventory;
    private Parent expiringView;
    private ExpiringSoonController expiring;
    private Parent creditView;
    private CreditController credit;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        stage.setTitle("TindaTrack");
        showLogin(stage);
        stage.show();
    }

    private void showLogin(Stage stage) throws IOException {
        sessions.clear();
        discardOwnerViews();
        stage.setMinWidth(460);
        stage.setMinHeight(540);
        FXMLLoader loader = loadView("login.fxml");
        LoginController controller = loader.getController();
        controller.configure(sessions, owner -> {
            try {
                showOwnerHome(stage, owner);
            } catch (IOException exception) {
                controller.showScreenError("the owner screen");
            }
        }, () -> {
            try {
                showGuestMode(stage);
            } catch (IOException exception) {
                controller.showScreenError("Guest Mode");
            }
        });
        if (stage.getScene() == null) {
            stage.setScene(createScene(loader.getRoot()));
        } else {
            stage.getScene().setRoot(loader.getRoot());
        }
    }

    private void showOwnerHome(Stage stage, OwnerSession owner) throws IOException {
        FXMLLoader homeLoader = loadView("owner-home.fxml");
        FXMLLoader navigationLoader = loadView("owner-navigation.fxml");
        ((OwnerHomeController) homeLoader.getController()).configure(owner.fullName());
        sessions.begin(owner);
        homeView = homeLoader.getRoot();
        products = new ProductDao(sessions, owner);
        credits = new CreditDao(sessions, owner);
        navigation = navigationLoader.getController();
        ownerLayout = new BorderPane();
        ownerLayout.setLeft(navigationLoader.getRoot());
        BorderPane signedInLayout = ownerLayout;
        navigation.configure(owner.fullName(), destination -> navigateOwner(stage, owner, destination), () -> {
            if (stage.getScene().getRoot() == signedInLayout && canLeaveOwnerView(stage)) {
                returnToLogin(stage);
            }
        });
        stage.getScene().setRoot(ownerLayout);
        // Allow room for the sidebar and the existing table columns.
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setWidth(Math.max(stage.getWidth(), 1000));
        stage.setHeight(Math.max(stage.getHeight(), 680));
        showOwnerScreen(stage, owner, Destination.DASHBOARD);
    }

    private void navigateOwner(Stage stage, OwnerSession owner, Destination destination) {
        try {
            sessions.requireActive(owner);
            if (canLeaveOwnerView(stage)) {
                showOwnerScreen(stage, owner, destination);
            }
        } catch (AccessDeniedException denied) {
            if (navigation != null) navigation.showError(denied.getMessage());
        } catch (IOException exception) {
            if (navigation != null) navigation.showError("Could not open the screen. Please try again.");
        }
    }

    private boolean canLeaveOwnerView(Stage stage) {
        if ((inventory != null && inventory.isDeleting())
                || Window.getWindows().stream().anyMatch(window -> window instanceof Stage dialog
                        && dialog.getOwner() == stage && dialog.isShowing())) {
            navigation.showError("Finish or cancel the current operation before leaving this screen.");
            return false;
        }
        return true;
    }

    /** Prepare a destination first, so a layout error leaves the current view usable. */
    private void showOwnerScreen(Stage stage, OwnerSession owner, Destination destination) throws IOException {
        sessions.requireActive(owner);
        if (destination == currentDestination) return;

        Parent next;
        Runnable enter = () -> { };
        Runnable leave = () -> { };
        switch (destination) {
            case DASHBOARD -> next = homeView;
            case INVENTORY -> {
                if (inventoryView == null) {
                    FXMLLoader loader = loadView("inventory.fxml");
                    inventory = loader.getController();
                    inventory.configure(products);
                    inventoryView = loader.getRoot();
                }
                next = inventoryView;
                enter = inventory::activate;
                leave = inventory::deactivate;
            }
            case EXPIRING -> {
                if (expiringView == null) {
                    FXMLLoader loader = loadView("expiring-soon.fxml");
                    expiring = loader.getController();
                    expiring.configure(products);
                    expiringView = loader.getRoot();
                }
                next = expiringView;
                enter = expiring::activate;
                leave = expiring::deactivate;
            }
            case CREDIT -> {
                if (creditView == null) {
                    FXMLLoader loader = loadView("credit.fxml");
                    credit = loader.getController();
                    credit.configure(credits);
                    creditView = loader.getRoot();
                }
                next = creditView;
                enter = credit::activate;
                leave = credit::deactivate;
            }
            default -> throw new IllegalArgumentException("Unknown owner destination.");
        }
        leaveCurrentView.run();
        ownerLayout.setCenter(next);
        currentDestination = destination;
        leaveCurrentView = leave;
        navigation.setCurrent(destination);
        enter.run();
    }

    private void discardOwnerViews() {
        leaveCurrentView.run();
        leaveCurrentView = () -> { };
        currentDestination = null;
        ownerLayout = null;
        navigation = null;
        homeView = null;
        inventoryView = null;
        inventory = null;
        expiringView = null;
        expiring = null;
        creditView = null;
        credit = null;
        products = null;
        credits = null;
    }

    private void showGuestMode(Stage stage) throws IOException {
        FXMLLoader loader = loadView("guest.fxml");
        GuestSession guest = new GuestSession();
        sessions.begin(guest);
        ((GuestController) loader.getController()).configure(
                new GuestService(sessions, guest), () -> returnToLogin(stage));
        stage.getScene().setRoot(loader.getRoot());
    }

    private void returnToLogin(Stage stage) {
        try {
            showLogin(stage);
        } catch (IOException exception) {
            stage.close();
        }
    }

    @Override
    public void stop() {
        sessions.clear();
        discardOwnerViews();
    }

    private FXMLLoader loadView(String name) throws IOException {
        URL resource = getClass().getResource("/tindatrack/fxml/" + name);
        if (resource == null) throw new IOException("The " + name + " layout could not be found.");
        FXMLLoader loader = new FXMLLoader(resource);
        loader.load();
        return loader;
    }

    private Scene createScene(Parent root) throws IOException {
        URL css = getClass().getResource("/tindatrack/css/login.css");
        if (css == null) throw new IOException("The login stylesheet could not be found.");
        Scene scene = new Scene(root, 640, 560);
        scene.getStylesheets().add(css.toExternalForm());
        return scene;
    }
}
