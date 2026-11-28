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
import tindatrack.core.DashboardDao;
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
    private static final boolean INTEGRATION = "true".equals(System.getenv("TINDATRACK_INTEGRATION_CHECKS"));
    private static final boolean DASHBOARD_CHECKS = "true".equals(System.getenv("TINDATRACK_DASHBOARD_CHECKS"));

    public static void main(String[] args) throws Exception {
        IsolatedDatabase.verify();
        boolean reopen = args.length > 0 && args[0].equals("reopen");
        if (!reopen && !INTEGRATION) {
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
            if (!reopen && INTEGRATION) registrationB4();
            if (!reopen) runCase("Login_WrongPassword", () -> {
                setText(stage, "#usernameField", "maggie");
                setText(stage, "#passwordField", "wrongpass");
                click("Login");
                waitFor(() -> !button(stage, "Login").isDisabled());
                check(text(stage, "#feedbackLabel").equals("Invalid username or password."), "Wrong-password feedback differed");
            }, "Existing test owner rejected wrongpass and remained on login.");
            if (!reopen && INTEGRATION) loginB4();
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
            if (INTEGRATION) dashboardConformance();
            if (DASHBOARD_CHECKS) dashboardRegression();
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
            if ("true".equals(System.getenv("TINDATRACK_VALIDATION_CHECKS"))) {
                runCase("Day6_ProductValidation", Day5UiCheck::productValidation,
                        "Blank names, invalid prices, stock overflow, invalid dates and duplicate names were rejected without adding products.");
                runCase("Day6_ExpirationConfirmationAndNull", Day5UiCheck::expirationValidation,
                        "Past-date No canceled the save; Yes saved once. Removing expiration saved NULL, excluded the item from Expiring Soon, and unchanged edits were rejected.");
                runCase("Day6_StaleProductAndDeleteCancel", Day5UiCheck::staleProductAndDeleteCancel,
                        "A stale edit did not overwrite a newer price; Cancel preserved the product; confirmed deletion removed only the test item.");
                runCase("Day6_ReadFailureRecovery", Day5UiCheck::readFailureRecovery,
                        "Inventory, expiration and credit failures showed safe messages; restored connections allowed refresh and cleared error styling.");
                runCase("Day6_LogoutScreenRecovery", Day5UiCheck::logoutScreenRecovery,
                        "A missing compiled login FXML cleared the session and showed Retry Login; restoring the file allowed a fresh login in the same window.");
            }
            if (INTEGRATION) {
                inventoryB4();
                expirationB4();
                benchmarkB4();
                runCase("Day6_ContinuousCreditFlow", Day5UiCheck::continuousCreditFlow,
                        "One customer moved from 250.00 to 280.00 after utang, 230.00 after partial payment, and 0.00 after full payment; history reconciled.");
                guestB4();
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

    private static void unavailable(CheckedAction action) throws Exception {
        // This process runs from the verified disposable server's configuration directory.
        Path config = Path.of("config/database.properties"); byte[] original = Files.readAllBytes(config);
        try (SocketChannel endpoint = SocketChannel.open()) {
            endpoint.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress)endpoint.getLocalAddress()).getPort();
            Files.writeString(config, "db.url=jdbc:mysql://127.0.0.1:" + port + "/tindatrack\ndb.username=test\ndb.password=test\n");
            action.run();
        } finally { Files.write(config, original); }
    }

    private static void registerForm() throws Exception {
        waitFor(() -> stage.getScene().lookup("#createAccountLink") != null
                && stage.getScene().lookup("#createAccountLink").isVisible());
        async(() -> { ((Hyperlink)stage.getScene().lookup("#createAccountLink")).fire(); return null; });
        waitFor(() -> findForm() != null);
        setText(form(), "#fullNameField", "Maggie Dadivalos"); setText(form(), "#usernameField", "maggie");
        setText(form(), "#passwordField", "store123"); setText(form(), "#confirmationField", "store123");
    }

    private static void cancelRegistration() throws Exception {
        pressForm("#cancelButton"); waitFor(() -> findForm() == null);
    }

    private static void registrationB4() throws Exception {
        runCase("CreateAccount_Cancel", () -> {
            registerForm(); cancelRegistration(); check(number("SELECT COUNT(*) FROM users") == 0, "Cancel created an owner");
        }, "Filled and canceled the owner form; users remained empty.");
        runCase("CreateAccount_EmptyField", () -> {
            registerForm(); setText(form(), "#usernameField", ""); pressForm("#createButton");
            check(visible(form(), "#usernameError"), "Missing username error");
            check(number("SELECT COUNT(*) FROM users") == 0, "Blank username saved"); cancelRegistration();
        }, "Blank username showed an inline error and created no account.");
        runCase("CreateAccount_ShortPassword", () -> {
            registerForm(); setText(form(), "#passwordField", "abc12"); setText(form(), "#confirmationField", "abc12");
            pressForm("#createButton"); check(text(form(), "#passwordError").equals("Password must be at least 6 characters."), "Short-password feedback differed");
            check(number("SELECT COUNT(*) FROM users") == 0, "Short password saved"); cancelRegistration();
        }, "abc12 was rejected with the specified six-character message; no owner was saved.");
        runCase("CreateAccount_PasswordMismatch", () -> {
            registerForm(); setText(form(), "#confirmationField", "store321"); pressForm("#createButton");
            check(text(form(), "#confirmationError").equals("Passwords do not match."), "Mismatch feedback differed");
            check(number("SELECT COUNT(*) FROM users") == 0, "Mismatch saved"); cancelRegistration();
        }, "store123/store321 showed Passwords do not match. and saved no owner.");
        runCase("CreateAccount_DatabaseError", () -> {
            registerForm(); Stage dialog = form();
            unavailable(() -> { pressForm("#createButton"); waitFor(() -> !((Button)dialog.getScene().lookup("#createButton")).isDisabled());
                check(text(dialog, "#formError").equals("Account was not created. Please try again."), "Account database error differed"); });
            check(number("SELECT COUNT(*) FROM users") == 0, "Failed registration saved"); cancelRegistration();
        }, "An unreachable isolated endpoint showed the specified account-save error and saved no owner.");
        runCase("CreateAccount_SunnyDay", () -> {
            registerForm(); Stage dialog = form(); pressForm("#createButton"); waitFor(() -> !dialog.isShowing());
            check(text(stage, "#feedbackLabel").equals("Account created. You can now log in."), "Missing registration success");
            check(number("SELECT COUNT(*) FROM users WHERE username='maggie'") == 1, "Owner creation differs");
        }, "Created Maggie Dadivalos / maggie / store123 through the real FXML form; it closed and confirmed account creation.");
        runCase("CreateAccount_PasswordHashed", () -> {
            try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement("SELECT password_hash FROM users WHERE username=?")) {
                s.setString(1, "maggie"); try (ResultSet r = s.executeQuery()) {
                    check(r.next(), "Owner missing"); String hash = r.getString(1);
                    check(!hash.equals("store123") && PasswordHasher.verify("store123".toCharArray(), hash), "Stored password is not a usable hash");
                }
            }
        }, "The stored value was not plaintext and verified using PasswordHasher; the hash was not printed.");
    }

    private static void loginB4() throws Exception {
        runCase("Login_EmptyFields", () -> {
            setText(stage, "#usernameField", ""); setText(stage, "#passwordField", ""); click("Login");
            check(text(stage, "#feedbackLabel").equals("Please fill in all fields."), "Blank login feedback differed");
        }, "Blank fields showed the specified required-fields feedback and stayed on Login.");
        runCase("Login_UnknownUsername", () -> {
            setText(stage, "#usernameField", "nobody"); setText(stage, "#passwordField", "store123"); click("Login");
            waitFor(() -> !button(stage, "Login").isDisabled());
            check(text(stage, "#feedbackLabel").equals("Invalid username or password."), "Unknown-user feedback differed");
        }, "nobody/store123 showed Invalid username or password.");
        runCase("Login_DatabaseError", () -> unavailable(() -> {
            setText(stage, "#usernameField", "maggie"); setText(stage, "#passwordField", "store123"); click("Login");
            waitFor(() -> !button(stage, "Login").isDisabled());
            check(text(stage, "#feedbackLabel").equals("Cannot connect to the database. Please try again."), "Login connection feedback differed");
        }), "An unreachable isolated endpoint showed the specified connection error without stopping the installed MySQL service.");
    }

    private static void dashboardConformance() throws Exception {
        runCase("SB02_DashboardConformance", () -> {
            click("Dashboard"); waitDashboard();
            check(fx(() -> stage.getScene().getRoot().lookupAll(".label").stream()
                    .filter(n -> n instanceof Label).map(n -> ((Label)n).getText()).toList()
                    .containsAll(List.of("Total Products", "Total Credits", "Expiring Soon"))), "SB02 card labels are missing");
            assertDashboardTotals();
        }, "SB02 contains all three summary cards; displayed product count, outstanding peso credit total and expired/next-seven-day count matched independent SQL reads.");
        click("Credit Management"); waitList();
    }

    private static void waitDashboard() throws Exception {
        waitFor(() -> stage.getScene().lookup("#dashboardRefreshButton") != null
                && !((Button)stage.getScene().lookup("#dashboardRefreshButton")).isDisabled());
    }

    private static void assertDashboardTotals() throws Exception {
        check(text(stage, "#totalProducts").equals(Integer.toString(number("SELECT COUNT(*) FROM products"))), "Dashboard product count differs");
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement("SELECT COALESCE(SUM(balance),0.00) FROM customers"); ResultSet r = s.executeQuery()) {
            check(r.next(), "Missing credit total");
            check(text(stage, "#totalCredits").equals("₱" + r.getBigDecimal(1).setScale(2).toPlainString()), "Dashboard credit total differs");
        }
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT COUNT(*) FROM products WHERE expiration_date IS NOT NULL AND expiration_date<=?")) {
            s.setObject(1, LocalDate.now().plusDays(7));
            try (ResultSet r = s.executeQuery()) { check(r.next() && text(stage, "#expiringProducts").equals(Long.toString(r.getLong(1))), "Dashboard expiration total differs"); }
        }
    }

    private static void refreshDashboard() throws Exception {
        fx(() -> { ((Button)stage.getScene().lookup("#dashboardRefreshButton")).fire(); return null; });
        waitDashboard();
    }

    private static void dashboardRegression() throws Exception {
        runCase("Day7_DashboardEmptyStore", () -> {
            try {
                IsolatedDatabase.execute("DELETE FROM payments");
                IsolatedDatabase.execute("DELETE FROM credit_transactions");
                IsolatedDatabase.execute("DELETE FROM customers");
                IsolatedDatabase.execute("DELETE FROM products");
                click("Dashboard"); waitDashboard();
                check(text(stage,"#totalProducts").equals("0") && text(stage,"#totalCredits").equals("₱0.00")
                        && text(stage,"#expiringProducts").equals("0"), "Empty store did not show real zero totals");
                assertDashboardTotals();
            } finally { IsolatedDatabase.resetCustomers(); IsolatedDatabase.seedProducts(); }
            refreshDashboard();
        }, "An empty isolated store displayed 0 products, ₱0.00 outstanding credit and 0 expiring items; the approved fixtures were restored.");
        runCase("Day7_DashboardRefreshOnReturn", () -> {
            ProductDao products = new ProductDao(sessions, owner);
            try {
                click("Inventory"); waitList();
                check(products.addProduct("Dashboard Test Product", new BigDecimal("1.00"),0,LocalDate.now().plusDays(7))==ProductDao.AddResult.ADDED,"Dashboard fixture add failed");
                new CreditService(dao).recordUtang(new UtangRequest(customer("Aling Nena").id(),"Aling Nena","Dashboard refresh test",new BigDecimal("10.00"),LocalDate.now()));
                click("Dashboard"); waitDashboard(); assertDashboardTotals();
                check(text(stage,"#totalCredits").equals("₱260.00"),"Dashboard did not include committed utang");
                click("Credit Management"); waitList(); pay("Aling Nena","3.00");
                click("Dashboard"); waitDashboard(); assertDashboardTotals();
                check(text(stage,"#totalCredits").equals("₱257.00"),"Dashboard did not reflect payment");
            } finally {
                for (Product p : products.findByName("Dashboard Test Product")) products.deleteProduct(p);
                IsolatedDatabase.resetNena();
            }
            refreshDashboard(); assertDashboardTotals();
        }, "Returning from Inventory/Credit refreshed all totals after a product, 10.00 utang and 3.00 payment; isolated fixtures were restored.");
        runCase("Day7_DashboardRetry", () -> {
            unavailable(() -> {
                refreshDashboard();
                check(text(stage,"#dashboardStatus").equals("Cannot load the store summary. Please try again."),"Dashboard error was unclear");
                check(text(stage,"#totalProducts").equals("—") && text(stage,"#totalCredits").equals("—")
                        && text(stage,"#expiringProducts").equals("—"),"A failed read displayed misleading totals");
            });
            refreshDashboard(); assertDashboardTotals();
            check(text(stage,"#dashboardStatus").equals("Store summary updated."),"Dashboard retry did not recover");
        }, "Connection failure left dashes and a safe retry message; Refresh Summary recovered and displayed the database totals.");
        runCase("Day7_DashboardLeaveDuringLoad", () -> {
            Parent oldHome = fx(() -> (Parent)((javafx.scene.layout.BorderPane)stage.getScene().getRoot()).getCenter());
            fx(() -> { ((Button)stage.getScene().lookup("#dashboardRefreshButton")).fire(); button(stage,"Inventory").fire(); return null; });
            waitList(); Thread.sleep(150);
            check(fx(() -> ((Label)oldHome.lookup("#totalProducts")).getText().equals("—")),"Obsolete summary callback repainted the departed Dashboard");
            click("Dashboard"); waitDashboard(); assertDashboardTotals();
            fx(() -> {
                Parent root=stage.getScene().getRoot(); root.applyCss(); root.layout();
                var image=root.snapshot(null,null);
                var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<output.getHeight();y++) for(int x=0;x<output.getWidth();x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                javax.imageio.ImageIO.write(output,"png",Path.of(System.getenv("TINDATRACK_TEST_TARGET"),"day7-task2-dashboard.png").toFile());
                return null;
            });
        }, "Leaving during a load cleared the old cards and ignored obsolete results; reopening refreshed correctly in the same window.");
        click("Credit Management"); waitList();
    }

    private static void resetB4Products() throws Exception {
        IsolatedDatabase.verify();
        IsolatedDatabase.execute("DELETE FROM products");
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement(
                "INSERT INTO products(name,price,stock_qty,expiration_date) VALUES(?,?,?,?)")) {
            Object[][] rows = {
                {"Lucky Me Pancit Canton", "15.00", 40, LocalDate.of(2027,3,1)},
                {"Coke Mismo 8oz", "12.00", 24, LocalDate.of(2027,1,15)},
                {"Safeguard Soap", "38.00", 12, null},
                {"Egg (per piece)", "10.00", 30, LocalDate.now().plusDays(5)},
                {"Nescafe 3-in-1", "8.00", 100, LocalDate.now().plusDays(20)},
                {"Bread Loaf", "55.00", 10, LocalDate.now().minusDays(2)},
                {"Expired test item", "12.00", 24, LocalDate.now().minusDays(1)}
            };
            for (Object[] row : rows) {
                s.setString(1, (String)row[0]); s.setBigDecimal(2, new BigDecimal((String)row[1]));
                s.setInt(3, (Integer)row[2]); s.setObject(4, row[3], java.sql.Types.DATE); s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static void inventorySearch(String search) throws Exception {
        setText(stage, "#searchField", search); click("Search"); waitList();
    }
    private static Product listedProduct() throws Exception {
        return fx(() -> (Product)((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().getFirst());
    }
    private static int listedProducts() throws Exception { return fx(() -> ((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().size()); }
    private static void newProduct(String name, String price, String stock) throws Exception {
        async(() -> { button(stage, "Add Product").fire(); return null; }); waitFor(() -> findForm() != null);
        setText(form(), "#nameField", name); setText(form(), "#priceField", price); setText(form(), "#stockField", stock);
    }
    private static void deleteListed(String decision) throws Exception {
        selectFirstProduct(); async(() -> { button(stage, "Delete Product").fire(); return null; }); waitFor(() -> findForm() != null);
        fx(() -> { button(findForm(), decision).fire(); return null; }); waitFor(() -> findForm() == null); waitList();
    }

    private static void inventoryB4() throws Exception {
        resetB4Products(); click("Inventory"); waitList(); inventorySearch("");
        runCase("AddProduct_Cancel", () -> { newProduct("Skyflakes Pack", "7.50", "60"); cancel();
            check(number("SELECT COUNT(*) FROM products WHERE name='Skyflakes Pack'") == 0, "Canceled add saved");
        }, "Canceled Skyflakes Pack / 7.50 / 60; no product was added.");
        runCase("AddProduct_EmptyField", () -> { newProduct("", "10.00", "5"); pressForm("#saveButton");
            check(visible(form(), "#nameError"), "Missing name error"); cancel();
        }, "Blank name with price 10.00 and stock 5 showed an inline error and saved nothing.");
        runCase("AddProduct_InvalidPrice", () -> { newProduct("Skyflakes Pack", "0", "60");
            for (String value : List.of("0", "-5", "abc")) { setText(form(), "#priceField", value); pressForm("#saveButton"); check(visible(form(), "#priceError"), "Invalid price accepted"); }
            cancel(); check(number("SELECT COUNT(*) FROM products WHERE name='Skyflakes Pack'") == 0, "Invalid price saved");
        }, "All three specified prices (0, -5, abc) showed Price errors without saving.");
        runCase("AddProduct_InvalidStock", () -> { newProduct("Skyflakes Pack", "7.50", "-1");
            for (String value : List.of("-1", "2.5", "abc")) { setText(form(), "#stockField", value); pressForm("#saveButton"); check(visible(form(), "#stockError"), "Invalid stock accepted"); }
            cancel(); check(number("SELECT COUNT(*) FROM products WHERE name='Skyflakes Pack'") == 0, "Invalid stock saved");
        }, "All three specified stock values (-1, 2.5, abc) showed Stock errors without saving.");
        runCase("AddProduct_DuplicateName", () -> { newProduct("Lucky Me Pancit Canton", "15.00", "10"); pressForm("#saveButton");
            waitFor(() -> !((Button)findForm().getScene().lookup("#saveButton")).isDisabled());
            check(text(form(), "#nameError").equals("This product already exists."), "Duplicate feedback differed"); cancel();
            check(number("SELECT COUNT(*) FROM products WHERE name='Lucky Me Pancit Canton' AND stock_qty=40") == 1, "Duplicate changed existing product");
        }, "Duplicate Lucky Me Pancit Canton was rejected; its single existing row and stock 40 were preserved.");
        runCase("AddProduct_DatabaseError", () -> { newProduct("Skyflakes Pack", "7.50", "60"); Stage dialog = form();
            unavailable(() -> { pressForm("#saveButton"); waitFor(() -> !((Button)dialog.getScene().lookup("#saveButton")).isDisabled());
                check(text(dialog, "#formError").equals("Product was not saved. Please try again."), "Add failure feedback differed"); }); cancel();
            check(number("SELECT COUNT(*) FROM products WHERE name='Skyflakes Pack'") == 0, "Failed add saved");
        }, "A valid add at an unreachable isolated endpoint showed the specified error and saved nothing.");
        runCase("AddProduct_SunnyDay", () -> { newProduct("Skyflakes Pack", "7.50", "60"); save();
            check(text(stage, "#statusLabel").equals("Product saved."), "Missing add success"); inventorySearch("Skyflakes Pack");
            Product p = listedProduct(); check(p.name().equals("Skyflakes Pack") && p.price().equals(new BigDecimal("7.50")) && p.stockQuantity()==60, "Saved product differs");
        }, "Skyflakes Pack appeared at 7.50 with stock 60 and Product saved. feedback.");
        runCase("SearchProduct_SunnyDay", () -> { inventorySearch("coke"); check(listedProduct().name().equals("Coke Mismo 8oz") && listedProduct().price().equals(new BigDecimal("12.00")) && listedProduct().stockQuantity()==24, "Coke search differs"); }, "coke found Coke Mismo 8oz with price 12.00 and stock 24.");
        runCase("SearchProduct_NotCaseSensitive", () -> { inventorySearch("PANCIT"); check(listedProduct().name().equals("Lucky Me Pancit Canton"), "Case-insensitive search differs"); }, "PANCIT found Lucky Me Pancit Canton.");
        runCase("SearchProduct_EmptyBox", () -> { inventorySearch(""); check(listedProducts()==number("SELECT COUNT(*) FROM products"), "Empty search omitted products"); }, "Blank search displayed the complete database product list.");
        runCase("SearchProduct_NoMatch", () -> { inventorySearch("xyz123"); check(listedProducts()==0 && fx(() -> ((Label)((TableView<?>)stage.getScene().lookup("#productsTable")).getPlaceholder()).getText()).equals("No products found."), "No-match placeholder differed"); }, "xyz123 displayed No products found. with an empty table.");
        runCase("SearchProduct_DatabaseError", () -> unavailable(() -> { inventorySearch("coke"); check(text(stage, "#statusLabel").startsWith("Search failed. Please try again."), "Search failure feedback differed"); }), "coke search at an unreachable isolated endpoint displayed the specified search error.");
        inventorySearch("Coke Mismo 8oz"); selectFirstProduct();
        runCase("UpdateProduct_EmptyField", () -> { openEditProduct(); setText(form(), "#priceField", ""); pressForm("#saveButton"); check(visible(form(), "#priceError"), "Empty price accepted"); cancel(); check(listedProduct().price().equals(new BigDecimal("12.00")), "Invalid edit changed price"); }, "Blank Price showed a field error and preserved Coke at 12.00.");
        runCase("UpdateProduct_InvalidValues", () -> { selectFirstProduct(); openEditProduct(); setText(form(), "#priceField", "0"); setText(form(), "#stockField", "abc"); pressForm("#saveButton"); check(visible(form(), "#priceError") && visible(form(), "#stockError"), "Invalid update errors missing"); cancel(); check(listedProduct().price().equals(new BigDecimal("12.00")) && listedProduct().stockQuantity()==24, "Invalid edit changed data"); }, "Price 0 and stock abc showed both field errors and kept the old values.");
        runCase("UpdateProduct_Cancel", () -> { selectFirstProduct(); openEditProduct(); setText(form(), "#priceField", "20.00"); cancel(); check(listedProduct().price().equals(new BigDecimal("12.00")), "Cancel changed price"); }, "Canceled price 20.00; Coke stayed at 12.00.");
        runCase("UpdateProduct_DatabaseError", () -> { selectFirstProduct(); openEditProduct(); setText(form(), "#priceField", "13.00"); Stage dialog = form();
            unavailable(() -> { pressForm("#saveButton"); waitFor(() -> !((Button)dialog.getScene().lookup("#saveButton")).isDisabled()); check(text(dialog, "#formError").equals("Changes were not saved. Please try again."), "Update failure feedback differed"); }); cancel();
            check(new ProductDao(sessions, owner).findByName("Coke Mismo 8oz").getFirst().price().equals(new BigDecimal("12.00")), "Failed update changed row");
        }, "An unreachable isolated endpoint rejected the update with the specified message; old price remained.");
        runCase("UpdateProduct_SunnyDay", () -> { selectFirstProduct(); openEditProduct(); setText(form(), "#priceField", "13.00"); setText(form(), "#stockField", "30"); save(); check(listedProduct().price().equals(new BigDecimal("13.00")) && listedProduct().stockQuantity()==30, "Update values differ"); }, "Coke Mismo 8oz refreshed to price 13.00 and stock 30.");
        inventorySearch("Safeguard Soap");
        runCase("DeleteProduct_Cancel", () -> { deleteListed("Cancel"); check(listedProducts()==1 && listedProduct().name().equals("Safeguard Soap"), "Cancel removed Safeguard"); }, "Canceled deletion; Safeguard Soap remained in the list and database.");
        runCase("DeleteProduct_DatabaseError", () -> { unavailable(() -> { deleteListed("Delete"); check(text(stage, "#statusLabel").equals("Product was not deleted. Please try again."), "Delete error differed"); }); check(new ProductDao(sessions, owner).findByName("Safeguard Soap").size()==1, "Failed delete removed row"); }, "Deletion at an unreachable isolated endpoint showed the specified message and preserved Safeguard Soap.");
        runCase("DeleteProduct_SunnyDay", () -> { int before = number("SELECT COUNT(*) FROM products"); refresh(); deleteListed("Delete"); check(listedProducts()==0 && number("SELECT COUNT(*) FROM products")==before-1 && new ProductDao(sessions, owner).findByName("Coke Mismo 8oz").size()==1, "Delete affected wrong rows"); }, "Confirmed Safeguard Soap deletion removed exactly its row and preserved Coke.");
        runCase("DeleteProduct_CreditRecordsUnchanged", () -> { resetNena(); var history = dao.findHistory(customer("Aling Nena").id()); inventorySearch("Lucky Me Pancit Canton"); deleteListed("Delete"); check(customer("Aling Nena").balance().equals(new BigDecimal("250.00")) && dao.findHistory(customer("Aling Nena").id()).equals(history), "Product delete changed credit"); }, "Deleted Lucky Me Pancit Canton; Aling Nena remained at 250.00 with identical credit history.");
        resetB4Products(); refresh();
    }

    private static void expirationRange(int days) throws Exception {
        fx(() -> { @SuppressWarnings("unchecked") ComboBox<Integer> range = (ComboBox<Integer>)stage.getScene().lookup("#rangeSelector"); range.setValue(days); return null; });
        waitList();
    }
    private static boolean expiringContains(String name) throws Exception {
        return fx(() -> ((TableView<?>)stage.getScene().lookup("#expiringTable")).getItems().stream().anyMatch(p -> ((Product)p).name().equals(name)));
    }
    private static void setProductDate(LocalDate date) throws Exception {
        fx(() -> { ((DatePicker)findForm().getScene().lookup("#expirationField")).setValue(date); return null; });
    }
    private static void confirmProductDate(String decision) throws Exception {
        Stage dialog = form(); async(() -> { ((Button)dialog.getScene().lookup("#saveButton")).fire(); return null; });
        waitFor(() -> pastDateAlert() != null);
        check(fx(() -> ((DialogPane)pastDateAlert().getScene().getRoot()).getContentText()).equals("This date has already passed. Save anyway?"), "Past-date prompt differed");
        fx(() -> { button(pastDateAlert(), decision).fire(); return null; }); waitFor(() -> pastDateAlert() == null);
        if (decision.equals("Yes")) { waitFor(() -> !dialog.isShowing()); waitList(); }
    }

    private static void expirationB4() throws Exception {
        click("Expiring Soon"); waitList(); expirationRange(7);
        runCase("ViewExpiring_SunnyDay", () -> {
            check(expiringContains("Egg (per piece)") && !expiringContains("Nescafe 3-in-1"), "Seven-day membership differs");
            fx(() -> { TableView<?> table=(TableView<?>)stage.getScene().lookup("#expiringTable");
                LocalDate previous=null;
                for (Object item : table.getItems()) { Product p=(Product)item; check(previous==null || !p.expirationDate().isBefore(previous), "Expiry sort differs"); previous=p.expirationDate();
                    if(p.name().equals("Egg (per piece)")) check(table.getColumns().get(3).getCellData(table.getItems().indexOf(item)).equals("5 Days Left"), "Egg days left differs"); }
                return null;
            });
        }, "Seven-day range listed Egg with 5 Days Left, excluded Nescafe at +20 days, and sorted dates nearest first (including expired rows).");
        runCase("ViewExpiring_ChangeRange", () -> { expirationRange(30); check(expiringContains("Nescafe 3-in-1"), "Thirty-day range omitted Nescafe"); }, "Changing to 30 days included Nescafe 3-in-1 at +20 days.");
        runCase("ViewExpiring_ExpiredHighlighted", () -> check(fx(() -> stage.getScene().getRoot().lookupAll(".table-row-cell").stream().anyMatch(n ->
                n instanceof TableRow<?> row && row.getItem() instanceof Product p && p.name().equals("Bread Loaf")
                && row.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("expired"))
                && row.getBackground().getFills().getFirst().getFill().equals(javafx.scene.paint.Color.web("#fde9e7")))), "Bread was not highlighted red"),
                "Bread Loaf at -2 days appeared with the expired state and the configured red row background.");
        runCase("ViewExpiring_NoExpiryNotListed", () -> check(!expiringContains("Safeguard Soap") && number("SELECT COUNT(*) FROM products WHERE name='Safeguard Soap' AND expiration_date IS NULL")==1, "NULL expiration was listed"), "Safeguard Soap had NULL expiration and was excluded from the list.");
        runCase("ViewExpiring_NoProducts", () -> {
            try { IsolatedDatabase.execute("UPDATE products SET expiration_date=NULL"); expirationRange(7); refresh();
                check(text(stage, "#listMessage").equals("No products expiring soon.") && fx(() -> ((TableView<?>)stage.getScene().lookup("#expiringTable")).getItems().isEmpty()), "Empty expiry feedback differed");
            } finally { resetB4Products(); }
            refresh();
        }, "With no dated products, seven-day filtering showed exactly No products expiring soon.; fixtures were restored.");
        runCase("ViewExpiring_DatabaseError", () -> unavailable(() -> { refresh(); check(text(stage, "#listMessage").equals("Cannot load the list. Please try again."), "Expiry database error differed"); }), "An unreachable isolated endpoint showed the specified expiration-list error.");
        click("Inventory"); waitList(); inventorySearch("Coke Mismo 8oz"); selectFirstProduct();
        runCase("SetExpiration_SunnyDay", () -> { openEditProduct(); setProductDate(LocalDate.of(2027,6,30)); save(); check(listedProduct().expirationDate().equals(LocalDate.of(2027,6,30)), "Saved date differs"); }, "2027-06-30 was saved and displayed for Coke Mismo 8oz.");
        runCase("SetExpiration_NoExpiration", () -> { selectFirstProduct(); openEditProduct(); fx(() -> { ((CheckBox)findForm().getScene().lookup("#noExpiration")).setSelected(true); check(findForm().getScene().lookup("#expirationField").isDisabled(), "Date field stayed enabled"); return null; }); save(); check(listedProduct().expirationDate()==null, "NULL date was not saved"); }, "Ticking No expiration date disabled the date field and saved NULL.");
        runCase("SetExpiration_InvalidDate", () -> { selectFirstProduct(); openEditProduct(); fx(() -> { ((DatePicker)findForm().getScene().lookup("#expirationField")).getEditor().setText("2026-13-45"); ((CheckBox)findForm().getScene().lookup("#noExpiration")).setSelected(false); return null; });
            pressForm("#saveButton"); check(text(form(), "#dateError").equals("Please enter a valid date."), "Invalid date feedback differed"); cancel(); check(listedProduct().expirationDate()==null, "Invalid date changed row");
        }, "2026-13-45 showed Please enter a valid date. and preserved the existing NULL date.");
        runCase("SetExpiration_PastDateNo", () -> { selectFirstProduct(); openEditProduct(); fx(() -> { ((CheckBox)findForm().getScene().lookup("#noExpiration")).setSelected(false); return null; }); setProductDate(LocalDate.of(2026,1,1)); confirmProductDate("No"); check(fx(() -> findForm()!=null), "No closed the form"); cancel(); check(listedProduct().expirationDate()==null, "No saved a past date"); }, "2026-01-01 prompted for confirmation; No returned to the form without saving.");
        runCase("SetExpiration_PastDateYes", () -> { selectFirstProduct(); openEditProduct(); fx(() -> { ((CheckBox)findForm().getScene().lookup("#noExpiration")).setSelected(false); return null; }); setProductDate(LocalDate.of(2026,1,1)); confirmProductDate("Yes"); check(listedProduct().expirationDate().equals(LocalDate.of(2026,1,1)), "Yes did not save the past date"); }, "2026-01-01 was saved only after the specified confirmation and Yes.");
        resetB4Products(); click("Credit Management"); waitList();
    }

    private static void benchmarkB4() throws Exception {
        IsolatedDatabase.verify();
        int baseline = number("SELECT COUNT(*) FROM products");
        check(baseline < 1000, "Unexpected fixture size");
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement s = c.prepareStatement(
                "INSERT INTO products(name,price,stock_qty,expiration_date) VALUES(?,1.00,1,NULL)")) {
            for(int i=baseline;i<1000;i++){ s.setString(1,"Day6 benchmark a "+i); s.addBatch(); } s.executeBatch();
        }
        click("Inventory"); waitList();
        runCase("SearchProduct_Speed", () -> {
            check(number("SELECT COUNT(*) FROM products")==1000,"Benchmark does not contain exactly 1000 products");
            java.util.ArrayList<Long> elapsed = new java.util.ArrayList<>();
            for(int i=0;i<3;i++) {
                setText(stage,"#searchField","a"); long start=System.nanoTime(); click("Search"); waitList();
                fx(() -> { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); return null; });
                elapsed.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
                check(listedProducts()==number("SELECT COUNT(*) FROM products WHERE LOCATE('a',name)>0"),"Benchmark results incomplete");
            }
            result("NFR02_Measurements","Pass","Exactly 1000 products; search a; query-to-JavaFX-layout milliseconds for three runs: "+elapsed+". Local test host only; no low-spec hardware certification.");
            check(elapsed.stream().allMatch(ms -> ms<=2000),"Search exceeded 2000 ms: "+elapsed);
        }, "Search a with exactly 1000 products completed within 2 seconds in all three local query-to-layout measurements.");
        IsolatedDatabase.execute("DELETE FROM products WHERE name LIKE 'Day6 benchmark a %'");
        check(number("SELECT COUNT(*) FROM products")==baseline,"Benchmark fixtures were not removed");
        click("Credit Management"); waitList();
    }

    private static void continuousCreditFlow() throws Exception {
        resetNena(); openUtang("Aling Nena"); setText(form(),"#itemsField","2 Pancit Canton"); setText(form(),"#amountField","30"); save(); assertBalance("Aling Nena","280.00");
        pay("Aling Nena","50"); assertBalance("Aling Nena","230.00"); pay("Aling Nena","230"); assertBalance("Aling Nena","0.00");
        var history=dao.findHistory(customer("Aling Nena").id());
        check(history.size()==4 && history.stream().filter(t -> t.type().equals("Payment")).count()==2,"Lifecycle history differs");
        BigDecimal total=history.stream().map(t -> t.type().equals("Payment")?t.amount().negate():t.amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
        check(total.signum()==0,"History does not reconcile to zero");
    }

    private static void guestB4() throws Exception {
        click("Logout"); click("Continue as Guest (View Only)");
        waitFor(() -> !((Button)stage.getScene().lookup("#searchButton")).isDisabled());
        runCase("GuestMode_SunnyDay", () -> {
            check(fx(() -> !((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().isEmpty()
                    && stage.getScene().getRoot().lookupAll(".label").stream().anyMatch(n -> n instanceof Label l && l.getText().contains("View only"))),"Guest products/banner missing");
            check(fx(() -> ((TableView<?>)stage.getScene().lookup("#productsTable")).getColumns().size()==3),"Guest projection differs");
        }, "Guest products displayed only product, peso price and stock with a View only banner.");
        runCase("GuestMode_SearchProduct", () -> { setText(stage,"#searchField","safeguard"); click("Search"); waitFor(() -> !((Button)stage.getScene().lookup("#searchButton")).isDisabled());
            check(fx(() -> ((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().stream().anyMatch(p -> p instanceof GuestService.ProductRow row && row.name().equals("Safeguard Soap") && row.price().equals(new BigDecimal("38.00")) && row.stockQuantity()==12)),"Guest Safeguard result differs");
        }, "safeguard found Safeguard Soap at 38.00 with stock 12.");
        runCase("GuestMode_SearchNoMatch", () -> { setText(stage,"#searchField","xyz123"); click("Search"); waitFor(() -> !((Button)stage.getScene().lookup("#searchButton")).isDisabled());
            check(fx(() -> ((TableView<?>)stage.getScene().lookup("#productsTable")).getItems().isEmpty()
                    && ((Label)((TableView<?>)stage.getScene().lookup("#productsTable")).getPlaceholder()).getText().equals("No products found.")),"Guest no-match feedback differs");
        }, "xyz123 showed No products found. in the guest table.");
        runCase("GuestMode_DatabaseError", () -> unavailable(() -> { setText(stage,"#searchField",""); click("Search"); waitFor(() -> !((Button)stage.getScene().lookup("#searchButton")).isDisabled());
            check(fx(() -> ((Label)((TableView<?>)stage.getScene().lookup("#productsTable")).getPlaceholder()).getText()).equals("Cannot load the data. Please try again."),"Guest database feedback differed");
        }), "An unreachable isolated endpoint showed the specified safe guest error.");
        runCase("Day6_GuestInventoryGuards", () -> {
            ProductDao oldOwnerProducts=new ProductDao(sessions,owner); Product sample=new Product(1,"Forbidden",BigDecimal.ONE,1,null);
            int before=number("SELECT COUNT(*) FROM products");
            expectDenied(() -> oldOwnerProducts.addProduct("Forbidden",BigDecimal.ONE,1,null));
            expectDenied(() -> oldOwnerProducts.updateProduct(sample,BigDecimal.TEN,2,LocalDate.now()));
            expectDenied(() -> oldOwnerProducts.deleteProduct(sample));
            check(number("SELECT COUNT(*) FROM products")==before,"Guest changed inventory");
        }, "Guest session was rejected by product add, update/expiration and delete logic before database changes.");
        runCase("GuestMode_ExitGuestMode", () -> { click("Exit Guest Mode"); check(fx(() -> stage.getScene().lookup("#usernameField")!=null),"Guest exit did not return Login"); sessions.requireSignedOut(); }, "Exit Guest Mode returned to Login and cleared the guest session.");
        login();
    }

    private static void addValidationProduct() throws Exception {
        click("Inventory"); waitList();
        async(() -> { button(stage, "Add Product").fire(); return null; });
        waitFor(() -> findForm() != null);
        setText(form(), "#nameField", "Task 4 Validation Item");
        setText(form(), "#priceField", "12.34");
        setText(form(), "#stockField", "3");
    }

    private static void productValidation() throws Exception {
        addValidationProduct(); long before = number("SELECT COUNT(*) FROM products");
        setText(form(), "#nameField", ""); pressForm("#saveButton");
        check(text(form(), "#nameError").equals("Enter a product name."), "Blank name was accepted");
        setText(form(), "#nameField", "Task 4 Validation Item");
        for (String value : List.of("0", "-1", "abc", "1.001", "100000000")) {
            setText(form(), "#priceField", value); pressForm("#saveButton");
            check(visible(form(), "#priceError"), "Invalid price accepted: " + value);
        }
        setText(form(), "#priceField", "12.34");
        for (String value : List.of("-1", "1.5", "abc", "2147483648")) {
            setText(form(), "#stockField", value); pressForm("#saveButton");
            check(visible(form(), "#stockError"), "Invalid stock accepted: " + value);
        }
        setText(form(), "#stockField", "3");
        fx(() -> { ((DatePicker)findForm().getScene().lookup("#expirationField")).getEditor().setText("2026-13-45"); return null; });
        pressForm("#saveButton"); check(text(form(), "#dateError").equals("Please enter a valid date."), "Invalid date accepted");
        fx(() -> { ((CheckBox)findForm().getScene().lookup("#noExpiration")).setSelected(true); return null; });
        setText(form(), "#nameField", "Lucky Me Pancit Canton"); pressForm("#saveButton");
        waitFor(() -> !((Button)findForm().getScene().lookup("#saveButton")).isDisabled());
        check(text(form(), "#nameError").equals("This product already exists."), "Duplicate name feedback differed");
        cancel(); check(number("SELECT COUNT(*) FROM products") == before, "Invalid input changed products");
    }

    private static Stage pastDateAlert() {
        return Window.getWindows().stream().filter(w -> w instanceof Stage s && s.isShowing()
                && s.getTitle().equals("Past expiration date")).map(w -> (Stage) w).findFirst().orElse(null);
    }

    private static void expirationValidation() throws Exception {
        addValidationProduct(); Stage productForm = form();
        fx(() -> { ((DatePicker)productForm.getScene().lookup("#expirationField")).setValue(LocalDate.now().minusDays(1)); return null; });
        async(() -> { ((Button)productForm.getScene().lookup("#saveButton")).fire(); return null; });
        waitFor(() -> pastDateAlert() != null);
        fx(() -> { button(pastDateAlert(), "No").fire(); return null; });
        waitFor(() -> pastDateAlert() == null);
        check(number("SELECT COUNT(*) FROM products WHERE name='Task 4 Validation Item'") == 0, "No saved a product");
        async(() -> { ((Button)productForm.getScene().lookup("#saveButton")).fire(); return null; });
        waitFor(() -> pastDateAlert() != null);
        fx(() -> { button(pastDateAlert(), "Yes").fire(); return null; });
        waitFor(() -> !productForm.isShowing()); waitList();
        check(number("SELECT COUNT(*) FROM products WHERE name='Task 4 Validation Item'") == 1, "Past-date save count differs");
        setText(stage, "#searchField", "Task 4 Validation Item"); click("Search"); waitList();
        selectFirstProduct(); openEditProduct();
        fx(() -> { ((CheckBox)findForm().getScene().lookup("#noExpiration")).setSelected(true); return null; }); save();
        check(number("SELECT COUNT(*) FROM products WHERE name='Task 4 Validation Item' AND expiration_date IS NULL") == 1, "NULL expiration was not saved");
        click("Expiring Soon"); waitList();
        check(fx(() -> ((TableView<?>)stage.getScene().lookup("#expiringTable")).getItems().stream()
                .noneMatch(p -> ((Product)p).name().equals("Task 4 Validation Item"))), "NULL expiration appeared in alerts");
        click("Inventory"); waitList(); selectFirstProduct(); openEditProduct();
        pressForm("#saveButton"); check(text(form(), "#formError").equals("No changes to save."), "Unchanged edit was submitted"); cancel();
    }

    private static void selectFirstProduct() throws Exception {
        fx(() -> { ((TableView<?>)stage.getScene().lookup("#productsTable")).getSelectionModel().select(0); return null; });
    }

    private static void openEditProduct() throws Exception {
        async(() -> { button(stage, "Edit Product").fire(); return null; }); waitFor(() -> findForm() != null);
    }

    private static void staleProductAndDeleteCancel() throws Exception {
        selectFirstProduct(); openEditProduct();
        ProductDao products = new ProductDao(sessions, owner);
        Product original = products.findByName("Task 4 Validation Item").getFirst();
        check(products.updateProduct(original, new BigDecimal("17.00"), original.stockQuantity(), null), "Test update failed");
        setText(form(), "#priceField", "20"); pressForm("#saveButton");
        waitFor(() -> !((Button)findForm().getScene().lookup("#saveButton")).isDisabled());
        check(text(form(), "#formError").contains("changed or was removed"), "Stale edit feedback missing");
        check(products.findByName(original.name()).getFirst().price().equals(new BigDecimal("17.00")), "Stale edit overwrote price");
        cancel(); refresh(); selectFirstProduct();
        async(() -> { button(stage, "Delete Product").fire(); return null; }); waitFor(() -> findForm() != null);
        fx(() -> { button(findForm(), "Cancel").fire(); return null; }); waitFor(() -> findForm() == null);
        check(products.findByName(original.name()).size() == 1, "Cancel deleted product");
        async(() -> { button(stage, "Delete Product").fire(); return null; }); waitFor(() -> findForm() != null);
        fx(() -> { button(findForm(), "Delete").fire(); return null; }); waitFor(() -> findForm() == null); waitList();
        check(products.findByName(original.name()).isEmpty(), "Confirmed delete failed");
    }

    private static void readFailureRecovery() throws Exception {
        setText(stage, "#searchField", ""); click("Search"); waitList();
        Path config = Path.of("config/database.properties"); byte[] original = Files.readAllBytes(config);
        try (SocketChannel endpoint = SocketChannel.open()) {
            endpoint.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = ((InetSocketAddress)endpoint.getLocalAddress()).getPort();
            Files.writeString(config, "db.url=jdbc:mysql://127.0.0.1:" + port + "/tindatrack\ndb.username=test\ndb.password=test\n");
            refresh();
            check(text(stage, "#statusLabel").contains("Could not load products."), "Missing inventory connection feedback");
            check(fx(() -> label(stage, "#statusLabel").getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("error"))), "Inventory error styling missing");
            click("Expiring Soon"); waitFor(() -> ((Button)stage.getScene().lookup("#refreshButton")).getText().equals("Retry"));
            check(text(stage, "#listMessage").equals("Cannot load the list. Please try again."), "Expiration retry feedback differed");
            click("Credit Management"); waitList();
            check(text(stage, "#statusLabel").contains("Could not load customer balances."), "Missing credit connection feedback");
        } finally { Files.write(config, original); }
        refresh();
        check(fx(() -> !label(stage, "#statusLabel").getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("error"))), "Credit error styling did not clear");
        click("Expiring Soon"); waitList();
        click("Inventory"); waitList();
        check(fx(() -> !label(stage, "#statusLabel").getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("error"))), "Inventory error styling did not clear");
        click("Credit Management"); waitList();
    }

    private static void logoutScreenRecovery() throws Exception {
        Path target = Path.of(System.getenv("TINDATRACK_TEST_TARGET")).toRealPath();
        Path compiled = target.resolve("classes/tindatrack/fxml/login.fxml");
        Path backup = compiled.resolveSibling("login-task4-backup.fxml");
        check(compiled.toRealPath().startsWith(target) && !Files.exists(backup), "Unsafe compiled FXML test path");
        Files.move(compiled, backup);
        try {
            click("Logout");
            check(fx(stage::isShowing), "Login loading failure closed the window");
            check(fx(() -> button(stage, "Retry Login").isVisible()), "Missing signed-out recovery action");
            sessions.requireSignedOut();
            expectDenied(() -> { sessions.requireActive(owner); return null; });
        } finally { Files.move(backup, compiled); }
        click("Retry Login"); login();
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
        if (DASHBOARD_CHECKS) {
            runCase("Day7_DashboardGuestDenied", () -> {
                expectDenied(() -> new DashboardDao(sessions, owner).readSummary(LocalDate.now()));
                check(fx(() -> stage.getScene().lookup("#totalCredits")==null), "Guest view exposed owner summary cards");
            }, "Guest view had no Dashboard totals and the owner summary DAO rejected the guest session.");
        }
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
        Platform.runLater(() -> {
            try {
                // New ScrollPane content joins the scene graph when its skin is laid out.
                // Render before querying, just as a user sees it before the next click.
                if (stage != null && stage.getScene() != null) {
                    Parent root = stage.getScene().getRoot();
                    root.applyCss();
                    root.layout();
                }
                future.complete(action.call());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
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
