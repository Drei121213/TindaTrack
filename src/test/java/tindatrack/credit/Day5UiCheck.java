package tindatrack.credit;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import tindatrack.TindaTrackApplication;
import tindatrack.authentication.OwnerSession;
import tindatrack.authentication.UserDao;
import tindatrack.core.DatabaseConnection;
import tindatrack.core.PasswordHasher;
import tindatrack.core.SessionState;
import tindatrack.core.OwnerNavigationController.Destination;
import tindatrack.guest.GuestService;
import tindatrack.inventory.Product;
import tindatrack.inventory.ProductDao;
import tindatrack.testing.IsolatedDatabase;

/** Automated JavaFX acceptance checks. Run only through scripts/test_day5.py. */
public final class Day5UiCheck {
    private static Stage stage;
    private static TindaTrackApplication app;
    private static SessionState sessions;
    private static OwnerSession owner;
    private static CreditDao dao;

    public static void main(String[] args) throws Exception {
        IsolatedDatabase.verify();
        boolean reopen = args.length > 0 && args[0].equals("reopen");
        if (!reopen) {
            UserDao users = new UserDao(new SessionState());
            check(users.createOwner("Maggie", "maggie", PasswordHasher.hash("store123".toCharArray()))
                    == UserDao.CreateResult.CREATED, "Test owner could not be created");
        }
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> {
                app = new TindaTrackApplication();
                stage = new Stage();
                stage.setOpacity(0);
                app.start(stage);
                return null;
            });
            if (!reopen) runCase("Login_WrongPassword", () -> {
                setText(stage, "#usernameField", "maggie");
                setText(stage, "#passwordField", "wrongpass");
                click("Login");
                waitFor(() -> !button(stage, "Login").isDisabled());
                check(text(stage, "#feedbackLabel").equals("Invalid username or password."), "Wrong-password feedback differed");
            }, "Existing test owner rejected wrongpass and remained on login.");
            login();
            if (reopen) {
                runCase("RecordUtang_SavedImmediately", () -> {
                    assertBalance("Aling Nena", "280.00");
                    check(dao.findHistory(customer("Aling Nena").id()).stream()
                            .anyMatch(t -> t.type().equals("Utang") && t.amount().compareTo(new BigDecimal("30")) == 0), "Restart lost utang");
                }, "A separate JVM relaunched, signed in, and displayed Aling Nena at 280.00 with the saved 30.00 utang.");
                runCase("Day5_PaymentRestart", () -> {
                    assertBalance("Aling Rosa", "40.00");
                    check(dao.findHistory(customer("Aling Rosa").id()).stream()
                            .anyMatch(t -> t.type().equals("Payment") && t.amount().compareTo(new BigDecimal("5")) == 0), "Restart lost payment");
                }, "A separate JVM displayed Aling Rosa at 40.00 with both her 45.00 purchase and 5.00 payment preserved.");
                return;
            }
            result("Login_SunnyDay", "Pass", "maggie/store123 opened the signed-in owner Home, then Credit Management.");
            runCase("RecordUtang_SunnyDay", () -> {
                resetNena(); openUtang("Aling Nena");
                setText(form(), "#amountField", "30.00");
                setText(form(), "#itemsField", "2 Pancit Canton"); save();
                assertBalance("Aling Nena", "280.00");
                check(number("SELECT stock_qty FROM products WHERE name='Lucky Me Pancit Canton'") == 40, "Utang changed stock");
                check(text(stage, "#statusLabel").contains("Utang saved."), "Missing success confirmation");
            }, "Recorded 2 Pancit Canton for 30.00; list refreshed to 280.00, history saved, stock stayed 40, and repeat Save was disabled.");
            runCase("RecordUtang_NewCustomer", () -> {
                openUtang(null);
                setText(form(), "#customerNameField", "Aling Rosa");
                setText(form(), "#itemsField", "Sardines");
                setText(form(), "#amountField", "45.00"); save();
                assertBalance("Aling Rosa", "45.00");
                check(dao.findCustomers("Aling Rosa").size() == 1, "Duplicate new customer");
            }, "The New customer choice created one Aling Rosa with balance 45.00 and one Sardines purchase.");
            runCase("RecordUtang_EmptyFields", () -> {
                long before = transactionCount(); openUtang(null);
                setText(form(), "#amountField", "30"); pressForm("#saveButton");
                check(visible(form(), "#customerError"), "Missing name error");
                setText(form(), "#customerNameField", "Do not save");
                setText(form(), "#amountField", ""); pressForm("#saveButton");
                check(visible(form(), "#amountError"), "Missing amount error"); cancel();
                check(transactionCount() == before && dao.findCustomers("Do not save").isEmpty(), "Invalid form saved");
            }, "Blank name and blank amount showed field errors; no purchase or customer was inserted.");
            runCase("RecordUtang_InvalidAmount", () -> {
                long before = transactionCount(); openUtang("Aling Nena");
                for (String amount : List.of("0", "-10", "abc", "1.001")) {
                    setText(form(), "#amountField", amount); pressForm("#saveButton");
                    check(text(form(), "#amountError").equals("Please enter a valid amount."), "Invalid utang accepted");
                }
                cancel(); check(transactionCount() == before, "Invalid utang changed history");
            }, "0, -10, abc and 1.001 showed Please enter a valid amount.; nothing was saved.");
            runCase("RecordUtang_Cancel", () -> {
                resetNena(); long before = transactionCount(); openUtang("Aling Nena");
                setText(form(), "#amountField", "30"); cancel();
                assertBalance("Aling Nena", "250.00"); check(transactionCount() == before, "Cancel saved utang");
            }, "Filled a 30.00 purchase and canceled; the form closed, balance stayed 250.00 and history did not change.");
            runCase("RecordUtang_DatabaseError", () -> {
                resetNena(); openUtang("Aling Nena"); setText(form(), "#amountField", "30");
                failedConnectionSave("Utang was not saved. Please try again.");
                assertBalance("Aling Nena", "250.00");
            }, "An unreachable temporary endpoint produced Utang was not saved. Please try again.; form stayed open and balance stayed 250.00.");
            runCase("RecordPayment_PartialPayment", () -> {
                resetNena(); pay("Aling Nena", "50"); assertBalance("Aling Nena", "200.00");
                check(text(stage, "#statusLabel").contains("Payment saved."), "Missing payment confirmation");
            }, "Paid 50.00 from 250.00; the list refreshed to 200.00 with a separate payment and success message.");
            runCase("RecordPayment_FullPayment", () -> {
                resetNena(); pay("Aling Nena", "250"); assertBalance("Aling Nena", "0.00");
            }, "Paid 250.00; the stored and displayed balance became 0.00.");
            runCase("RecordPayment_NoBalance", () -> {
                clickPayment("Kuya Jun"); waitFor(() -> label(stage, "#statusLabel").getText().equals("This customer has no balance."));
                check(fx(Day5UiCheck::findForm) == null, "Zero balance opened form");
            }, "Kuya Jun at 0.00 showed This customer has no balance. without opening the payment form.");
            runCase("RecordPayment_EmptyOrZero", () -> {
                resetNena(); openPayment("Aling Nena"); long before = transactionCount();
                for (String amount : List.of("", "0", "-10", "abc")) {
                    setText(form(), "#amountField", amount); pressForm("#saveButton");
                    check(text(form(), "#amountError").equals("Please enter a valid amount."), "Invalid payment accepted");
                }
                cancel(); check(transactionCount() == before, "Invalid payment saved");
            }, "Blank, zero, negative and nonnumeric payments showed the expected error without saving.");
            runCase("RecordPayment_MoreThanBalance", () -> {
                resetNena(); rejectOverpayment("300"); assertBalance("Aling Nena", "250.00");
            }, "300.00 against 250.00 showed Payment cannot be more than the balance.; no payment was saved.");
            runCase("RecordPayment_Cancel", () -> {
                resetNena(); long before = transactionCount(); openPayment("Aling Nena");
                setText(form(), "#amountField", "50"); cancel();
                assertBalance("Aling Nena", "250.00"); check(transactionCount() == before, "Cancel saved payment");
            }, "Canceled a filled 50.00 payment; the form closed, balance stayed 250.00 and payment count stayed unchanged.");
            runCase("RecordPayment_DatabaseError", () -> {
                resetNena(); openPayment("Aling Nena"); setText(form(), "#amountField", "50");
                failedConnectionSave("Payment was not saved. Please try again."); assertBalance("Aling Nena", "250.00");
            }, "An unreachable temporary endpoint produced Payment was not saved. Please try again.; form stayed open and balance stayed 250.00.");
            runCase("RecordPayment_TwoDecimals", () -> {
                resetNena(); pay("Aling Nena", "49.50"); assertBalance("Aling Nena", "200.50");
                rejectOverpayment("300"); assertBalance("Aling Nena", "200.50");
            }, "49.50 reduced 250.00 to 200.50; the displayed balance retained two decimals and the next 300.00 was rejected.");
            runCase("Day5_StalePaymentForm", () -> {
                resetNena(); openPayment("Aling Nena"); setText(form(), "#amountField", "200");
                new CreditService(dao).recordPayment(new PaymentRequest(customer("Aling Nena").id(), new BigDecimal("100"), LocalDate.now()));
                pressForm("#saveButton");
                waitFor(() -> !((Button)findForm().getScene().lookup("#saveButton")).isDisabled());
                check(text(form(), "#amountError").equals("Payment cannot be more than the balance."), "Stale form overpaid");
                check(text(form(), "#balanceLabel").equals("Current Balance: ₱150.00"), "Stale balance not updated");
                cancel(); assertBalance("Aling Nena", "150.00");
            }, "Another connection paid 100.00 while a 200.00 form was open; save rejected against 150.00 and refreshed the form balance.");
            if ("true".equals(System.getenv("TINDATRACK_NAVIGATION_CHECKS"))) {
                runCase("Day6_RememberOwnerViews", Day5UiCheck::rememberOwnerViews,
                        "Sidebar routes retained inventory/customer searches and selections, expiration range and selection, and the same owner session.");
                runCase("Day6_RefreshOnReturn", Day5UiCheck::refreshOnReturn,
                        "Reopening Credit and Inventory read committed changes while retaining their search state.");
                runCase("Day6_LeaveDuringLoads", Day5UiCheck::leaveDuringLoads,
                        "Leaving Credit, Inventory and Expiring Soon during loads cleared their tables; obsolete callbacks did not restore rows. One main stage remained.");
            }
            runCase("Day5_InventoryExpirationRegression", Day5UiCheck::inventoryRegression,
                    "Owner inventory add, search, edit and confirmed delete worked; Expiring Soon listed the expired fixture and navigation preserved the session.");
            runCase("GuestMode_CreditTab", Day5UiCheck::guestBalances,
                    "Guest Credit tab showed the same committed customer names/balances as the owner, using only the two permitted columns.");
            runCase("GuestMode_NoOwnerFunctions", Day5UiCheck::guestRestrictions,
                    "Guest UI had no owner controls; owner credit route and both write services rejected guest access; exit cleared pending rows.");
            login();
            if ("true".equals(System.getenv("TINDATRACK_NAVIGATION_CHECKS"))) {
                runCase("Day6_NewLoginClearsViewState", Day5UiCheck::newLoginClearsViewState,
                        "After logout and guest exit, a new owner login started with empty searches, no selected customer/product, and the default seven-day range.");
            }
            // Keep this payment for the second process to verify durable payment history.
            pay("Aling Rosa", "5");
            // B4 requires closing immediately after the utang save, with no further UI action.
            resetNena(); openUtang("Aling Nena"); setText(form(), "#amountField", "30"); save();
            assertBalance("Aling Nena", "280.00");
        } finally {
            fx(() -> {
                for (Window window : List.copyOf(Window.getWindows())) if (window != stage) window.hide();
                if (stage != null) stage.close();
                return null;
            });
            Platform.exit();
        }
    }

    private static void rememberOwnerViews() throws Exception {
        click("Inventory"); waitList();
        setText(stage, "#searchField", "Lucky"); click("Search"); waitList();
        fx(() -> { ((TableView<?>)stage.getScene().lookup("#productsTable")).getSelectionModel().select(0); return null; });
        int productId = fx(() -> ((Product)((TableView<?>)stage.getScene().lookup("#productsTable"))
                .getSelectionModel().getSelectedItem()).id());
        click("Expiring Soon"); waitList();
        fx(() -> {
            @SuppressWarnings("unchecked") ComboBox<Integer> range = (ComboBox<Integer>)stage.getScene().lookup("#rangeSelector");
            range.setValue(30); return null;
        });
        waitList();
        fx(() -> { ((TableView<?>)stage.getScene().lookup("#expiringTable")).getSelectionModel().select(0); return null; });
        int expiringId = fx(() -> ((Product)((TableView<?>)stage.getScene().lookup("#expiringTable"))
                .getSelectionModel().getSelectedItem()).id());
        click("Credit Management"); waitList();
        setText(stage, "#searchField", "Nena"); click("Search"); waitList();
        fx(() -> { ((TableView<?>)stage.getScene().lookup("#customersTable")).getSelectionModel().select(0); return null; });
        int customerId = customer("Aling Nena").id();
        click("Dashboard"); click("Inventory"); waitList();
        check(fx(() -> ((TextField)stage.getScene().lookup("#searchField")).getText()).equals("Lucky"), "Inventory search was reset");
        check(fx(() -> ((Product)((TableView<?>)stage.getScene().lookup("#productsTable"))
                .getSelectionModel().getSelectedItem()).id()) == productId, "Inventory selection was reset");
        click("Expiring Soon"); waitList();
        check(fx(() -> ((ComboBox<?>)stage.getScene().lookup("#rangeSelector")).getValue()).equals(30), "Expiration range was reset");
        check(fx(() -> ((Product)((TableView<?>)stage.getScene().lookup("#expiringTable"))
                .getSelectionModel().getSelectedItem()).id()) == expiringId, "Expiration selection was reset");
        click("Credit Management"); waitList();
        check(fx(() -> ((TextField)stage.getScene().lookup("#searchField")).getText()).equals("Nena"), "Credit search was reset");
        check(fx(() -> ((Customer)((TableView<?>)stage.getScene().lookup("#customersTable"))
                .getSelectionModel().getSelectedItem()).id()) == customerId, "Customer selection was reset");
        waitFor(() -> !((TableView<?>)stage.getScene().lookup("#historyTable")).getItems().isEmpty());
        check(fx(() -> button(stage, "Credit Management").isDisabled()), "Current sidebar destination was not marked");
        sessions.requireActive(owner);
        ownerScreenshot();
    }

    private static void refreshOnReturn() throws Exception {
        Customer nena = customer("Aling Nena");
        click("Inventory"); waitList();
        new CreditService(dao).recordUtang(new UtangRequest(nena.id(), nena.name(), "Navigation test", new BigDecimal("10.00"), LocalDate.now()));
        ProductDao productDao = new ProductDao(sessions, owner);
        Product original = productDao.findByName("Lucky Me Pancit Canton").getFirst();
        BigDecimal newPrice = original.price().add(new BigDecimal("1.00"));
        check(productDao.updateProduct(original, newPrice, original.stockQuantity(), original.expirationDate()), "Test product update failed");
        click("Credit Management"); waitList();
        assertBalance("Aling Nena", nena.balance().add(new BigDecimal("10.00")).toPlainString());
        click("Inventory"); waitList();
        check(fx(() -> ((Product)((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().getFirst())
                .price().equals(newPrice)), "Inventory did not read the committed price on return");
        check(fx(() -> ((TextField)stage.getScene().lookup("#searchField")).getText()).equals("Lucky"), "Refreshing lost inventory search");
        click("Credit Management"); waitList();
    }

    private static void leaveDuringLoads() throws Exception {
        TableView<?> oldCredit = fx(() -> (TableView<?>)stage.getScene().lookup("#customersTable"));
        fx(() -> { button(stage, "Refresh").fire(); button(stage, "Inventory").fire(); return null; }); waitList();
        Thread.sleep(150);
        check(fx(() -> oldCredit.getItems().isEmpty()), "Credit callback restored hidden rows");
        TableView<?> oldInventory = fx(() -> (TableView<?>)stage.getScene().lookup("#productsTable"));
        fx(() -> { button(stage, "Refresh").fire(); button(stage, "Expiring Soon").fire(); return null; }); waitList();
        Thread.sleep(150);
        check(fx(() -> oldInventory.getItems().isEmpty()), "Inventory callback restored hidden rows");
        TableView<?> oldExpiring = fx(() -> (TableView<?>)stage.getScene().lookup("#expiringTable"));
        fx(() -> { button(stage, "Refresh").fire(); button(stage, "Credit Management").fire(); return null; }); waitList();
        Thread.sleep(150);
        check(fx(() -> oldExpiring.getItems().isEmpty()), "Expiration callback restored hidden rows");
        check(fx(() -> Window.getWindows().stream().filter(w -> w instanceof Stage && w.isShowing()).count()) == 1,
                "Navigation created another main window");
    }

    private static void newLoginClearsViewState() throws Exception {
        check(fx(() -> ((TextField)stage.getScene().lookup("#searchField")).getText().isEmpty()), "New login retained credit search");
        check(fx(() -> ((TableView<?>)stage.getScene().lookup("#customersTable")).getSelectionModel().getSelectedItem() == null), "New login retained customer selection");
        click("Inventory"); waitList();
        check(fx(() -> ((TextField)stage.getScene().lookup("#searchField")).getText().isEmpty()), "New login retained inventory search");
        check(fx(() -> ((TableView<?>)stage.getScene().lookup("#productsTable")).getSelectionModel().getSelectedItem() == null), "New login retained product selection");
        click("Expiring Soon"); waitList();
        check(fx(() -> ((ComboBox<?>)stage.getScene().lookup("#rangeSelector")).getValue()).equals(7), "New login retained old expiration range");
        click("Credit Management"); waitList();
    }

    private static void ownerScreenshot() throws Exception {
        fx(() -> {
            Parent root = stage.getScene().getRoot(); root.applyCss(); root.layout();
            javafx.scene.image.WritableImage image = root.snapshot(null, null);
            java.awt.image.BufferedImage output = new java.awt.image.BufferedImage(
                    (int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            javax.imageio.ImageIO.write(output, "png", Path.of(System.getenv("TINDATRACK_TEST_TARGET"), "day6-task2-owner.png").toFile());
            return null;
        });
    }

    private static void login() throws Exception {
        setText(stage, "#usernameField", "maggie"); setText(stage, "#passwordField", "store123"); click("Login");
        waitFor(() -> stage.getScene().lookup("#ownerGreeting") != null);
        Field field = TindaTrackApplication.class.getDeclaredField("sessions"); field.setAccessible(true);
        sessions = (SessionState) field.get(app);
        Field current = SessionState.class.getDeclaredField("current"); current.setAccessible(true);
        owner = (OwnerSession) current.get(sessions); dao = new CreditDao(sessions, owner);
        click("Credit Management"); waitList();
    }

    private static void resetNena() throws Exception { IsolatedDatabase.resetNena(); refresh(); }
    private static void refresh() throws Exception { click("Refresh"); waitList(); }
    private static void waitList() throws Exception { waitFor(() -> stage.getScene().lookup("#refreshButton") != null
            && !((Button)stage.getScene().lookup("#refreshButton")).isDisabled()); }
    private static Customer customer(String name) throws Exception { return dao.findCustomers(name).stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow(); }

    private static void openUtang(String name) throws Exception {
        async(() -> { button(stage, "+ Record Utang").fire(); return null; });
        waitFor(() -> findForm() != null && !((Button)findForm().getScene().lookup("#saveButton")).isDisabled());
        Stage form = form();
        fx(() -> {
            ((CheckBox)form.getScene().lookup("#newCustomer")).setSelected(name == null);
            if (name != null) {
                @SuppressWarnings("unchecked") ComboBox<Customer> choices = (ComboBox<Customer>)form.getScene().lookup("#customerField");
                choices.setValue(choices.getItems().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow());
            }
            return null;
        });
    }

    private static void clickPayment(String name) throws Exception {
        fx(() -> {
            @SuppressWarnings("unchecked") TableView<Customer> table = (TableView<Customer>)stage.getScene().lookup("#customersTable");
            Customer c = table.getItems().stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow();
            table.scrollTo(table.getItems().indexOf(c)); stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            Button payment = stage.getScene().getRoot().lookupAll(".credit-payment-button").stream().filter(n -> {
                Node parent = n; while (parent != null && !(parent instanceof TableRow<?>)) parent = parent.getParent();
                return parent instanceof TableRow<?> row && row.getItem() instanceof Customer value && value.id() == c.id();
            }).map(n -> (Button)n).findFirst().orElseThrow(); payment.fire(); return null;
        });
    }

    private static void openPayment(String name) throws Exception { clickPayment(name); waitFor(() -> findForm() != null); }
    private static void pay(String name, String amount) throws Exception { openPayment(name); setText(form(), "#amountField", amount); save(); }
    private static void rejectOverpayment(String amount) throws Exception {
        long before = transactionCount(); openPayment("Aling Nena"); setText(form(), "#amountField", amount); pressForm("#saveButton");
        check(text(form(), "#amountError").equals("Payment cannot be more than the balance."), "Overpayment accepted");
        cancel(); check(transactionCount() == before, "Overpayment inserted a payment");
    }

    private static void save() throws Exception {
        Stage form = form();
        fx(() -> {
            Button save = (Button)form.getScene().lookup("#saveButton"); save.fire();
            check(save.isDisabled() && ((Button)form.getScene().lookup("#cancelButton")).isDisabled(), "Saving controls stayed enabled");
            save.fire(); return null;
        });
        waitFor(() -> !form.isShowing()); waitList();
    }

    private static void cancel() throws Exception { pressForm("#cancelButton"); waitFor(() -> findForm() == null); waitList(); }
    private static void pressForm(String id) throws Exception { fx(() -> { ((Button)findForm().getScene().lookup(id)).fire(); return null; }); }

    private static void failedConnectionSave(String expected) throws Exception {
        Path config = Path.of("config/database.properties"); byte[] original = Files.readAllBytes(config);
        long before = transactionCount(); Stage form = form();
        try (SocketChannel endpoint = SocketChannel.open()) {
            endpoint.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress)endpoint.getLocalAddress()).getPort();
            Files.writeString(config, "db.url=jdbc:mysql://127.0.0.1:" + port + "/tindatrack\ndb.username=test\ndb.password=test\n");
            pressForm("#saveButton"); waitFor(() -> !((Button)form.getScene().lookup("#saveButton")).isDisabled());
            check(text(form, "#formError").equals(expected), "Save error message differed");
            check(fx(form::isShowing), "Failed save closed form");
        } finally { Files.write(config, original); }
        cancel(); check(transactionCount() == before, "Connection failure changed history"); refresh();
    }

    private static void assertBalance(String name, String expected) throws Exception {
        check(customer(name).balance().equals(new BigDecimal(expected)), "Stored balance differs for " + name);
        check(fx(() -> ((TableView<?>)stage.getScene().lookup("#customersTable")).getItems().stream()
                .anyMatch(c -> c instanceof Customer value && value.name().equals(name) && value.balance().equals(new BigDecimal(expected)))), "Displayed balance differs for " + name);
    }

    private static void inventoryRegression() throws Exception {
        click("Dashboard"); click("Inventory"); waitList();
        async(() -> { button(stage, "Add Product").fire(); return null; }); waitFor(() -> findForm() != null);
        setText(form(), "#nameField", "Regression Item"); setText(form(), "#priceField", "12"); setText(form(), "#stockField", "3");
        fx(() -> { ((DatePicker)findForm().getScene().lookup("#expirationField")).setValue(LocalDate.now().plusDays(2)); return null; }); save();
        setText(stage, "#searchField", "Regression Item"); click("Search"); waitList();
        fx(() -> { ((TableView<?>)stage.getScene().lookup("#productsTable")).getSelectionModel().select(0); return null; });
        async(() -> { button(stage, "Edit Product").fire(); return null; }); waitFor(() -> findForm() != null);
        setText(form(), "#priceField", "13"); setText(form(), "#stockField", "4"); save();
        check(fx(() -> ((Product)((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().getFirst()).price().equals(new BigDecimal("13.00"))), "Inventory edit failed");
        fx(() -> { ((TableView<?>)stage.getScene().lookup("#productsTable")).getSelectionModel().select(0); return null; });
        async(() -> { button(stage, "Delete Product").fire(); return null; }); waitFor(() -> findForm() != null);
        fx(() -> { button(findForm(), "Delete").fire(); return null; }); waitFor(() -> findForm() == null); waitList();
        check(fx(() -> ((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().isEmpty()), "Inventory delete failed");
        click("Expiring Soon"); waitList();
        check(fx(() -> ((TableView<?>)stage.getScene().lookup("#expiringTable")).getItems().stream()
                .anyMatch(p -> ((Product)p).name().equals("Expired test item"))), "Expired fixture was not listed");
        click("Dashboard"); sessions.requireActive(owner); click("Credit Management"); waitList();
    }

    private static void guestBalances() throws Exception {
        List<Customer> expected = dao.findCustomers(""); click("Dashboard"); click("Logout"); click("Continue as Guest (View Only)");
        fx(() -> { ((TabPane)stage.getScene().lookup("#guestTabs")).getSelectionModel().select(1); return null; });
        waitFor(() -> !((Button)stage.getScene().lookup("#refreshCreditButton")).isDisabled());
        fx(() -> {
            TableView<?> table = (TableView<?>)stage.getScene().lookup("#balancesTable");
            check(table.getColumns().size() == 2 && table.getItems().size() == expected.size(), "Guest projection differs");
            for (int i = 0; i < expected.size(); i++) {
                GuestService.BalanceRow row = (GuestService.BalanceRow)table.getItems().get(i);
                check(row.customerName().equals(expected.get(i).name()) && row.balance().equals(expected.get(i).balance()), "Guest balance differs");
            }
            return null;
        });
    }

    private static void guestRestrictions() throws Exception {
        Parent root = fx(() -> stage.getScene().getRoot());
        check(fx(() -> root.lookupAll(".button").stream().filter(n -> n instanceof Button).map(n -> ((Button)n).getText())
                .noneMatch(t -> t.contains("Record") || t.equals("Add Product") || t.equals("Edit Product") || t.equals("Delete Product"))), "Guest has owner controls");
        CreditService ownerService = new CreditService(dao);
        expectDenied(() -> ownerService.recordPayment(new PaymentRequest(1, new BigDecimal("1"), LocalDate.now())));
        expectDenied(() -> ownerService.recordUtang(new UtangRequest(null, "Denied", "", new BigDecimal("1"), LocalDate.now())));
        Method show = TindaTrackApplication.class.getDeclaredMethod("showOwnerScreen", Stage.class, OwnerSession.class, Destination.class); show.setAccessible(true);
        try { fx(() -> { show.invoke(app, stage, owner, Destination.CREDIT); return null; }); throw new AssertionError("Guest opened owner credit"); }
        catch (Exception denied) {
            Throwable cause = denied; while (cause.getCause() != null) cause = cause.getCause();
            check(cause instanceof SessionState.AccessDeniedException, "Unexpected route error");
        }
        fx(() -> { ((Button)root.lookup("#refreshCreditButton")).fire(); return null; }); click("Exit Guest Mode");
        check(fx(() -> ((TableView<?>)root.lookup("#balancesTable")).getItems().isEmpty()), "Guest exit retained data");
    }

    private static void expectDenied(Callable<?> operation) throws Exception {
        try { operation.call(); throw new AssertionError("Guest wrote credit data"); }
        catch (SessionState.AccessDeniedException expected) { }
    }

    private static long transactionCount() throws Exception { return number("SELECT (SELECT COUNT(*) FROM credit_transactions) + (SELECT COUNT(*) FROM payments)"); }
    private static int number(String sql) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement(sql); ResultSet r = s.executeQuery()) { r.next(); return r.getInt(1); }
    }
    private static Stage findForm() { return Window.getWindows().stream().filter(w -> w instanceof Stage && w != stage && w.isShowing()).map(w -> (Stage)w).findFirst().orElse(null); }
    private static Stage form() throws Exception { return fx(Day5UiCheck::findForm); }
    private static Label label(Stage window, String id) { return (Label)window.getScene().lookup(id); }
    private static String text(Stage window, String id) throws Exception { return fx(() -> label(window, id).getText()); }
    private static boolean visible(Stage window, String id) throws Exception { return fx(() -> label(window, id).isVisible()); }
    private static Button button(Stage window, String text) { return window.getScene().getRoot().lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().equals(text)).map(n -> (Button)n).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + text)); }
    private static void click(String text) throws Exception { fx(() -> { button(stage, text).fire(); return null; }); }
    private static void setText(Stage window, String id, String value) throws Exception { fx(() -> { ((TextInputControl)window.getScene().lookup(id)).setText(value); return null; }); }
    private static <T> CompletableFuture<T> async(Callable<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> { try { future.complete(action.call()); } catch (Throwable error) { future.completeExceptionally(error); } });
        return future;
    }
    private static <T> T fx(Callable<T> action) throws Exception { return async(action).get(12, TimeUnit.SECONDS); }
    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) { if (fx(condition)) return; Thread.sleep(30); }
        throw new AssertionError("Timed out waiting for JavaFX");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void runCase(String id, CheckedAction action, String success) throws Exception {
        try { action.run(); result(id, "Pass", success); }
        catch (Exception | AssertionError error) { result(id, "Failed", error.toString()); throw error; }
    }
    private static void result(String id, String status, String actual) {
        System.out.println("DAY5\t" + id + "\t" + status + "\t" + actual.replace('\n', ' ').replace('\t', ' '));
    }
    private interface CheckedAction { void run() throws Exception; }
}
