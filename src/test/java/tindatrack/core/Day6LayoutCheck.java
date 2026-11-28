package tindatrack.core;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import tindatrack.credit.Customer;
import tindatrack.credit.CreditTransaction;
import tindatrack.guest.GuestService.ProductRow;
import tindatrack.inventory.Product;

/** Offscreen FXML checks with in-memory rows only; never opens a database. */
public final class Day6LayoutCheck {
    private static final Path OUTPUT = Path.of("target", "day6-task3-ui");

    public static void main(String[] args) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.startup(() -> {
            try {
                Files.createDirectories(OUTPUT);
                for (int width : new int[] {1024, 1366}) {
                    for (String name : List.of("owner-home", "inventory", "expiring-soon", "credit", "login", "guest")) {
                        checkView(name, width);
                    }
                }
                for (String name : List.of("register", "product-form", "record-utang", "record-payment")) {
                    checkForm(name, 500, 600);
                    checkForm(name, 420, 460);
                }
                done.complete(null);
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        try {
            done.get(60, TimeUnit.SECONDS);
        } finally {
            Platform.exit();
        }
    }

    private static FXMLLoader load(String name) throws Exception {
        FXMLLoader loader = new FXMLLoader(Day6LayoutCheck.class.getResource("/tindatrack/fxml/" + name + ".fxml"));
        loader.load();
        return loader;
    }

    private static void checkView(String name, int windowWidth) throws Exception {
        String id = name + "-" + windowWidth + "x768";
        try {
            FXMLLoader loader = load(name);
            Parent view = loader.getRoot();
            boolean owner = List.of("owner-home", "inventory", "expiring-soon", "credit").contains(name);
            if (name.equals("expiring-soon")) {
                var reference = loader.getController().getClass().getDeclaredField("referenceDate");
                reference.setAccessible(true);
                reference.set(loader.getController(), LocalDate.now());
            }
            populate(loader, name);
            Parent root = view;
            if (owner) {
                FXMLLoader navigation = load("owner-navigation");
                OwnerNavigationController controller = navigation.getController();
                controller.configure("Maggie Dadivalos", destination -> { }, () -> { });
                controller.setCurrent(switch (name) {
                    case "inventory" -> OwnerNavigationController.Destination.INVENTORY;
                    case "expiring-soon" -> OwnerNavigationController.Destination.EXPIRING;
                    case "credit" -> OwnerNavigationController.Destination.CREDIT;
                    default -> OwnerNavigationController.Destination.DASHBOARD;
                });
                BorderPane layout = new BorderPane(view);
                layout.setLeft(navigation.getRoot());
                root = layout;
            }
            // Leave room for ordinary Windows borders and title bar at the target resolution.
            prepare(root, windowWidth - 16, 728);
            // A snapshot completes nested SplitPane/TableView layout before inspecting cells.
            snapshot(root, id);
            checkControls(root);
            for (Node node : root.lookupAll(".table-view")) {
                TableView<?> table = (TableView<?>) node;
                double columns = table.getColumns().stream().mapToDouble(TableColumn::getWidth).sum();
                require(columns <= table.getWidth() + 1, "Table columns exceed the available width");
            }
            if (name.equals("inventory") || name.equals("guest")) {
                require(hasCell(root, "₱15.00"), "Price lost peso notation or decimal places");
            }
            if (name.equals("credit")) {
                require(hasCell(root, "₱250.00") && hasCell(root, "₱0.00"), "Credit/zero balance formatting differs");
            }
            if (name.equals("owner-home")) {
                for (String label : List.of("totalProducts", "totalCredits", "expiringProducts")) {
                    Label value = (Label) loader.getNamespace().get(label);
                    Bounds bounds = value.localToScene(value.getBoundsInLocal());
                    require(bounds.getMaxX() <= root.getLayoutBounds().getWidth() && bounds.getMaxY() <= root.getLayoutBounds().getHeight(), "Dashboard value is clipped");
                }
            }
            if (name.equals("expiring-soon")) {
                require(hasCell(root, "Expired") && hasCell(root, "5 Days Left"), "Expiration text is missing");
                require(highlightedRows(root, "expired") == 1, "Expired highlight differs");
                require(highlightedRows(root, "expiring") == 1, "Upcoming highlight differs");
            }
            result(id, "Pass", "Controls and table widths fit; rendered with in-memory examples.");
        } catch (Exception | AssertionError failure) {
            result(id, "Failed", failure.toString());
            throw failure;
        }
    }

    private static void checkForm(String name, int width, int height) throws Exception {
        String id = name + "-" + width + "x" + height;
        try {
            FXMLLoader loader = load(name);
            Parent root = loader.getRoot();
            CheckBox newCustomer = (CheckBox) loader.getNamespace().get("newCustomer");
            if (newCustomer != null) newCustomer.setSelected(true);
            if (name.equals("record-payment")) {
                ((Label) loader.getNamespace().get("customerLabel")).setText("Customer: Aling Nena");
                ((Label) loader.getNamespace().get("balanceLabel")).setText("Current Balance: ₱250.00");
                ((Label) loader.getNamespace().get("newBalanceLabel")).setText("New Balance: ₱200.00");
            }
            for (Object value : loader.getNamespace().values()) {
                if (!(value instanceof Label label) || !label.getStyleClass().contains("registration-error")) continue;
                label.setText("Please check this field and try again.");
                label.setVisible(true);
                label.setManaged(true);
            }
            prepare(root, width - 16, height - 40);
            snapshot(root, id);
            checkControls(root);
            Node actions = root.lookup(".form-actions");
            require(actions != null && actions.getBoundsInParent().getHeight() >= 50, "Missing fixed form actions");
            ScrollPane scroll = (ScrollPane) root.lookup(".scroll-pane");
            require(scroll.getViewportBounds().getHeight() > 120, "Form has no usable field area");
            scroll.setVvalue(1);
            root.layout();
            checkControls(root);
            result(id, "Pass", "Save/Create and Cancel fit with validation messages visible; fields scroll.");
        } catch (Exception | AssertionError failure) {
            result(id, "Failed", failure.toString());
            throw failure;
        }
    }

    private static void prepare(Parent root, int width, int height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(Day6LayoutCheck.class.getResource("/tindatrack/css/login.css").toExternalForm());
        root.resize(width, height);
        root.applyCss();
        root.layout();
    }

    private static void checkControls(Parent root) {
        for (Node node : root.lookupAll(".button")) {
            if (!(node instanceof Button button) || !visible(node) || insideScroll(node)) continue;
            Bounds bounds = node.localToScene(node.getBoundsInLocal());
            require(bounds.getMinX() >= -1 && bounds.getMaxX() <= root.getLayoutBounds().getWidth() + 1
                    && bounds.getMinY() >= -1 && bounds.getMaxY() <= root.getLayoutBounds().getHeight() + 1,
                    "Button outside window: " + button.getText());
            require(button.getWidth() + 1 >= button.minWidth(-1), "Button text is clipped: " + button.getText());
        }
    }

    private static boolean visible(Node node) {
        for (Node parent = node; parent != null; parent = parent.getParent()) if (!parent.isVisible()) return false;
        return true;
    }

    private static boolean insideScroll(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane || parent instanceof TableView<?>) return true;
        }
        return false;
    }

    private static boolean hasCell(Parent root, String text) {
        return root.lookupAll(".table-cell").stream().anyMatch(node -> node instanceof TableCell<?, ?> cell && text.equals(cell.getText()));
    }

    private static long highlightedRows(Parent root, String state) {
        // Node.lookupAll does not reliably filter pseudo-classes; inspect the row state itself.
        return root.lookupAll(".table-row-cell").stream()
                .filter(row -> row.getPseudoClassStates().contains(PseudoClass.getPseudoClass(state))).count();
    }

    @SuppressWarnings("unchecked")
    private static <T> void rows(FXMLLoader loader, String id, List<T> rows) {
        TableView<T> table = (TableView<T>) loader.getNamespace().get(id);
        require(table != null, "Missing table: " + id);
        table.getItems().setAll(rows);
    }

    private static void populate(FXMLLoader loader, String name) {
        LocalDate today = LocalDate.now();
        List<Product> products = List.of(
                new Product(1, "Lucky Me Pancit Canton", new BigDecimal("15.00"), 40, today.plusDays(5)),
                new Product(2, "Expired test item", new BigDecimal("12.00"), 24, today.minusDays(1)));
        if (name.equals("inventory")) rows(loader, "productsTable", products);
        if (name.equals("expiring-soon")) {
            rows(loader, "expiringTable", products);
            ((Label) loader.getNamespace().get("rangeLabel")).setText("Expired products and dates from " + today + " through " + today.plusDays(7));
        }
        if (name.equals("credit")) {
            rows(loader, "customersTable", List.of(
                    new Customer(1, "Aling Nena", new BigDecimal("250.00"), LocalDateTime.of(today, java.time.LocalTime.of(8, 0))),
                    new Customer(2, "Kuya Jun", new BigDecimal("0.00"), null)));
            rows(loader, "historyTable", List.of(new CreditTransaction(1, "Utang", "2 Pancit Canton", new BigDecimal("30.00"), LocalDateTime.of(today, java.time.LocalTime.of(8, 0)))));
        }
        if (name.equals("guest")) rows(loader, "productsTable", List.of(new ProductRow("Lucky Me Pancit Canton", new BigDecimal("15.00"), 40)));
        if (name.equals("owner-home")) {
            ((Label) loader.getNamespace().get("ownerGreeting")).setText("Welcome, Maggie!");
            ((Label) loader.getNamespace().get("totalProducts")).setText("1000");
            ((Label) loader.getNamespace().get("totalCredits")).setText("₱199999999.98");
            ((Label) loader.getNamespace().get("expiringProducts")).setText("12");
        }
        if (name.equals("login")) {
            ((Node) loader.getNamespace().get("createAccountLink")).setVisible(true);
            ((Label) loader.getNamespace().get("feedbackLabel")).setText("Invalid username or password.");
            ((Node) loader.getNamespace().get("feedbackLabel")).pseudoClassStateChanged(PseudoClass.getPseudoClass("error"), true);
        }
    }

    private static void snapshot(Parent root, String name) throws Exception {
        WritableImage image = root.snapshot(null, null);
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) {
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        ImageIO.write(output, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void result(String id, String status, String actual) {
        System.out.println("LAYOUT\t" + id + "\t" + status + "\t" + actual.replace('\n', ' '));
    }
}
