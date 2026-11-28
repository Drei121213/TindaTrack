package tindatrack.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tindatrack.authentication.OwnerSession;
import tindatrack.guest.GuestSession;
import tindatrack.testing.IsolatedDatabase;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only summary checks; fixtures can change only on the verified test server. */
@EnabledIfEnvironmentVariable(named = "TINDATRACK_ISOLATED_TESTS", matches = "true")
class DashboardDatabaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 28);
    private SessionState sessions;
    private DashboardDao dashboard;

    @BeforeAll static void checkServer() throws Exception { IsolatedDatabase.verify(); }

    @BeforeEach void emptyFixtures() throws Exception {
        IsolatedDatabase.execute("DELETE FROM payments");
        IsolatedDatabase.execute("DELETE FROM credit_transactions");
        IsolatedDatabase.execute("DELETE FROM customers");
        IsolatedDatabase.execute("DELETE FROM products");
        sessions = new SessionState();
        OwnerSession owner = new OwnerSession(1, "Dashboard test owner");
        sessions.begin(owner);
        dashboard = new DashboardDao(sessions, owner);
    }

    @Test void emptyStoreShowsRealZeros() throws Exception {
        DashboardSummary value = dashboard.readSummary(TODAY);
        assertEquals(0, value.totalProducts());
        assertEquals(new BigDecimal("0.00"), value.totalCredits());
        assertEquals(0, value.expiringProducts());
        assertEquals(TODAY.plusDays(7), value.throughDate());
    }

    @Test void countsProductsRatherThanStockUnits() throws Exception {
        IsolatedDatabase.execute("INSERT INTO products(name,price,stock_qty) VALUES ('A',1,0),('B',2,100)");
        assertEquals(2, dashboard.readSummary(TODAY).totalProducts());
    }

    @Test void sumsCurrentBalancesWithoutCountingHistoryAgain() throws Exception {
        IsolatedDatabase.resetCustomers();
        IsolatedDatabase.execute("INSERT INTO customers(name,balance) VALUES ('Aling Rosa',45.50)");
        assertEquals(new BigDecimal("295.50"), dashboard.readSummary(TODAY).totalCredits());
    }

    @Test void includesExpiredTodayAndDaySevenButExcludesNullAndDayEight() throws Exception {
        IsolatedDatabase.execute("""
                INSERT INTO products(name,price,stock_qty,expiration_date) VALUES
                ('Expired',1,1,'2026-11-27'), ('Today',1,1,'2026-11-28'),
                ('Day seven',1,1,'2026-12-05'), ('Day eight',1,1,'2026-12-06'),
                ('No date',1,1,NULL)
                """);
        DashboardSummary value = dashboard.readSummary(TODAY);
        assertEquals(5, value.totalProducts());
        assertEquals(3, value.expiringProducts());
    }

    @Test void totalCreditMayExceedOneCustomersColumnLimit() throws Exception {
        IsolatedDatabase.execute("INSERT INTO customers(name,balance) VALUES ('Aling Nena',99999999.99),('Mang Tonyo',99999999.99)");
        assertEquals(new BigDecimal("199999999.98"), dashboard.readSummary(TODAY).totalCredits());
    }

    @Test void repeatedReadsDoNotChangeTheSummaryOrHistory() throws Exception {
        IsolatedDatabase.resetCustomers();
        DashboardSummary first = dashboard.readSummary(TODAY);
        assertEquals(first, dashboard.readSummary(TODAY));
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM credit_transactions");
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    @Test void guestCannotReadOwnerSummary() {
        sessions.clear(); sessions.begin(new GuestSession());
        assertThrows(SessionState.AccessDeniedException.class, () -> dashboard.readSummary(TODAY));
    }

    @Test void endedOwnerCannotReadSummary() {
        sessions.clear();
        assertThrows(SessionState.AccessDeniedException.class, () -> dashboard.readSummary(TODAY));
    }
}
