package tindatrack.credit;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tindatrack.authentication.OwnerSession;
import tindatrack.core.DatabaseConnection;
import tindatrack.core.SessionState;
import tindatrack.guest.GuestService;
import tindatrack.guest.GuestSession;
import tindatrack.testing.IsolatedDatabase;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL tests; skipped unless launched against a verified disposable server. */
@EnabledIfEnvironmentVariable(named = "TINDATRACK_ISOLATED_TESTS", matches = "true")
class CreditDatabaseTest {

    private SessionState sessions;
    private CreditDao dao;
    private CreditService service;
    private int nena;

    @BeforeAll
    static void requireIsolatedServer() throws Exception {
        IsolatedDatabase.verify();
        IsolatedDatabase.seedProducts();
    }

    @BeforeEach
    void prepareCustomers() throws Exception {
        IsolatedDatabase.resetCustomers();
        sessions = new SessionState();
        OwnerSession owner = new OwnerSession(1, "Database test owner");
        sessions.begin(owner);
        dao = new CreditDao(sessions, owner);
        service = new CreditService(dao);
        nena = dao.findCustomers("Aling Nena").getFirst().id();
    }

    @Test
    void existingUtangIncreasesOnlyTheCorrectBalanceAndPreservesStock() throws Exception {
        service.recordUtang(utang(nena, "Aling Nena", "30.00"));
        assertEquals(new BigDecimal("280.00"), balance(nena));
        assertEquals(new BigDecimal("0.00"), dao.findCustomers("Kuya Jun").getFirst().balance());
        assertEquals(2, count("credit_transactions"));
        assertEquals(40, number("SELECT stock_qty FROM products WHERE name = 'Lucky Me Pancit Canton'"));
        assertEquals("2 Pancit Canton", dao.findHistory(nena).getFirst().items());
    }

    @Test
    void newCustomerAndPurchaseAreSavedTogether() throws Exception {
        int id = service.recordUtang(utang(null, "Aling Rosa", "45.00"));
        assertEquals(new BigDecimal("45.00"), balance(id));
        assertEquals(1, dao.findCustomers("Aling Rosa").size());
        assertEquals(1, dao.findHistory(id).size());
    }

    @Test
    void typedExistingNameReusesTheCustomer() throws Exception {
        int id = service.recordUtang(utang(null, "aling nena", "30"));
        assertEquals(nena, id);
        assertEquals(2, count("customers"));
        assertEquals(new BigDecimal("280.00"), balance(nena));
    }

    @Test
    void partialPaymentSavesASeparateRecordAndPreservesUtang() throws Exception {
        assertEquals(new BigDecimal("200.00"), service.recordPayment(payment("50")));
        assertEquals(new BigDecimal("200.00"), balance(nena));
        assertEquals(1, count("payments"));
        assertEquals(1, count("credit_transactions"));
        assertEquals(new BigDecimal("50.00"), dao.findHistory(nena).getFirst().amount());
    }

    @Test
    void fullPaymentEndsAtZeroAndRejectsFurtherPayment() throws Exception {
        assertEquals(new BigDecimal("0.00"), service.recordPayment(payment("250")));
        assertThrows(CreditRules.PaymentBalanceException.class, () -> service.recordPayment(payment("0.01")));
        assertEquals(new BigDecimal("0.00"), balance(nena));
        assertEquals(1, count("payments"));
    }

    @Test
    void overpaymentAndInvalidRequestsLeaveStoredDataUnchanged() throws Exception {
        assertThrows(CreditRules.PaymentBalanceException.class, () -> service.recordPayment(payment("300")));
        assertThrows(CreditRules.ValidationException.class,
                () -> service.recordPayment(new PaymentRequest(nena, new BigDecimal("-10"), LocalDate.now())));
        assertThrows(CreditRules.ValidationException.class,
                () -> service.recordUtang(new UtangRequest(null, "", "", new BigDecimal("30"), LocalDate.now())));
        assertEquals(new BigDecimal("250.00"), balance(nena));
        assertEquals(0, count("payments"));
        assertEquals(1, count("credit_transactions"));
    }

    @Test
    void decimalPaymentKeepsExactMoneyAndRejectsNextOverpayment() throws Exception {
        assertEquals(new BigDecimal("200.50"), service.recordPayment(payment("49.50")));
        assertThrows(CreditRules.PaymentBalanceException.class, () -> service.recordPayment(payment("300")));
        assertEquals(new BigDecimal("200.50"), balance(nena));
        assertEquals(1, count("payments"));
    }

    @Test
    void utangRollsBackAfterThePurchaseInsertIfBalanceUpdateFails() throws Exception {
        withFailingBalanceUpdate(() -> assertThrows(SQLException.class,
                () -> service.recordUtang(utang(nena, "Aling Nena", "30"))));
        assertEquals(new BigDecimal("250.00"), balance(nena));
        assertEquals(1, count("credit_transactions"));
    }

    @Test
    void failedNewCustomerUtangRollsBackTheCustomerAsWell() throws Exception {
        withFailingBalanceUpdate(() -> assertThrows(SQLException.class,
                () -> service.recordUtang(utang(null, "Aling Rosa", "45"))));
        assertTrue(dao.findCustomers("Aling Rosa").isEmpty());
        assertEquals(2, count("customers"));
        assertEquals(1, count("credit_transactions"));
    }

    @Test
    void paymentRollsBackAfterThePaymentInsertIfBalanceUpdateFails() throws Exception {
        withFailingBalanceUpdate(() -> assertThrows(SQLException.class, () -> service.recordPayment(payment("50"))));
        assertEquals(new BigDecimal("250.00"), balance(nena));
        assertEquals(0, count("payments"));
        assertEquals(1, count("credit_transactions"));
    }

    @Test
    void simultaneousPaymentsCannotOverdrawTheCustomer() throws Exception {
        List<Boolean> results = concurrently(() -> tryPayment("150"), () -> tryPayment("150"));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(new BigDecimal("100.00"), balance(nena));
        assertEquals(1, count("payments"));
    }

    @Test
    void simultaneousUtangDoesNotLoseEitherBalanceIncrease() throws Exception {
        concurrently(() -> { service.recordUtang(utang(nena, "Aling Nena", "30")); return true; },
                () -> { service.recordUtang(utang(nena, "Aling Nena", "45")); return true; });
        assertEquals(new BigDecimal("325.00"), balance(nena));
        assertEquals(3, count("credit_transactions"));
    }

    @Test
    void simultaneousNewNameCreatesOneCustomerWithBothPurchases() throws Exception {
        concurrently(() -> { service.recordUtang(utang(null, "Aling Rosa", "30")); return true; },
                () -> { service.recordUtang(utang(null, "Aling Rosa", "45")); return true; });
        List<Customer> rosa = dao.findCustomers("Aling Rosa");
        assertEquals(1, rosa.size());
        assertEquals(new BigDecimal("75.00"), rosa.getFirst().balance());
        assertEquals(2, dao.findHistory(rosa.getFirst().id()).size());
    }

    @Test
    void removedCustomerAndBalanceOverflowCannotSave() throws Exception {
        assertThrows(CreditRules.ValidationException.class,
                () -> service.recordPayment(new PaymentRequest(Integer.MAX_VALUE, new BigDecimal("1"), LocalDate.now())));
        IsolatedDatabase.execute("UPDATE customers SET balance = 99999999.99 WHERE name = 'Aling Nena'");
        assertThrows(CreditRules.ValidationException.class, () -> service.recordUtang(utang(nena, "Aling Nena", "0.01")));
        assertEquals(new BigDecimal("99999999.99"), balance(nena));
        assertEquals(1, count("credit_transactions"));
    }

    @Test
    void guestSeesCommittedBalancesButCannotSaveAnything() throws Exception {
        service.recordUtang(utang(nena, "Aling Nena", "30"));
        service.recordPayment(payment("50"));
        sessions.clear();
        GuestSession guest = new GuestSession();
        sessions.begin(guest);
        GuestService guests = new GuestService(sessions, guest);
        assertEquals(new BigDecimal("230.00"), guests.findBalances().stream()
                .filter(c -> c.customerName().equals("Aling Nena")).findFirst().orElseThrow().balance());
        assertThrows(SessionState.AccessDeniedException.class, () -> service.recordPayment(payment("1")));
        assertThrows(SessionState.AccessDeniedException.class, () -> service.recordUtang(utang(null, "Denied", "1")));
        assertEquals(2, count("customers"));
        assertEquals(1, count("payments"));
    }

    @Test
    void earlierDatesAndLatestTransactionIncludeBothHistoryTypes() throws Exception {
        LocalDate earlier = LocalDate.now().minusDays(2);
        service.recordUtang(new UtangRequest(nena, "Aling Nena", "Earlier purchase", new BigDecimal("30"), earlier));
        service.recordPayment(new PaymentRequest(nena, new BigDecimal("10"), LocalDate.now()));
        assertEquals(earlier, dao.findHistory(nena).stream().filter(t -> "Earlier purchase".equals(t.items()))
                .findFirst().orElseThrow().date().toLocalDate());
        assertEquals(LocalDate.now(), dao.findCustomer(nena).orElseThrow().lastTransaction().toLocalDate());
    }

    private UtangRequest utang(Integer id, String name, String amount) {
        return service.prepareUtang(id, name, "2 Pancit Canton", amount, LocalDate.now());
    }

    private PaymentRequest payment(String amount) {
        return new PaymentRequest(nena, new BigDecimal(amount), LocalDate.now());
    }

    private BigDecimal balance(int id) throws Exception {
        return dao.findCustomer(id).orElseThrow().balance();
    }

    private boolean tryPayment(String amount) throws Exception {
        try {
            service.recordPayment(payment(amount));
            return true;
        } catch (CreditRules.PaymentBalanceException expected) {
            return false;
        }
    }

    private List<Boolean> concurrently(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> a = () -> { ready.countDown(); start.await(); return first.call(); };
            Callable<Boolean> b = () -> { ready.countDown(); start.await(); return second.call(); };
            Future<Boolean> left = workers.submit(a);
            Future<Boolean> right = workers.submit(b);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        }
    }

    private void withFailingBalanceUpdate(CheckedAction action) throws Exception {
        IsolatedDatabase.execute("""
                CREATE TRIGGER day5_fail_balance BEFORE UPDATE ON customers
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Injected balance update failure'
                """);
        try {
            action.run();
        } finally {
            IsolatedDatabase.execute("DROP TRIGGER day5_fail_balance");
        }
    }

    private int count(String table) throws Exception {
        return number("SELECT COUNT(*) FROM " + table); // Names are test constants, never form input.
    }

    private int number(String sql) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
