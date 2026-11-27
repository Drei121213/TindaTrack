package tindatrack.testing;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import tindatrack.core.DatabaseConnection;

/** Refuses fixture writes unless this is the launcher's temporary MySQL instance. */
public final class IsolatedDatabase {

    private IsolatedDatabase() {
    }

    public static void verify() throws Exception {
        if (!"true".equals(System.getenv("TINDATRACK_ISOLATED_TESTS"))) {
            throw new IllegalStateException("Use scripts/test_day5.py to run isolated database checks.");
        }
        Path expected = Path.of(System.getenv("TINDATRACK_TEST_DATADIR")).toRealPath();
        Path target = Path.of(System.getenv("TINDATRACK_TEST_TARGET")).toRealPath();
        if (!expected.startsWith(target) || expected.equals(target)) {
            throw new IllegalStateException("Test data directory must be inside project target.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT @@datadir");
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            if (!Path.of(rows.getString(1)).toRealPath().equals(expected)) {
                throw new IllegalStateException("Refusing to write fixtures to a different MySQL server.");
            }
        }
    }

    public static void execute(String sql) throws Exception {
        verify();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    public static void resetCustomers() throws Exception {
        verify();
        execute("DELETE FROM payments");
        execute("DELETE FROM credit_transactions");
        execute("DELETE FROM customers");
        execute("INSERT INTO customers (name, balance) VALUES ('Aling Nena', 250.00), ('Kuya Jun', 0.00)");
        execute("""
                INSERT INTO credit_transactions (customer_id, items, amount, transaction_date)
                SELECT customer_id, 'Opening balance', 250.00, '2026-09-28 08:00:00'
                FROM customers WHERE name = 'Aling Nena'
                """);
    }

    public static void resetNena() throws Exception {
        verify();
        execute("DELETE FROM payments WHERE customer_id IN (SELECT customer_id FROM customers WHERE name = 'Aling Nena')");
        execute("DELETE FROM credit_transactions WHERE customer_id IN (SELECT customer_id FROM customers WHERE name = 'Aling Nena')");
        execute("UPDATE customers SET balance = 250.00 WHERE name = 'Aling Nena'");
        execute("""
                INSERT INTO credit_transactions (customer_id, items, amount, transaction_date)
                SELECT customer_id, 'Opening balance', 250.00, '2026-09-28 08:00:00'
                FROM customers WHERE name = 'Aling Nena'
                """);
    }

    public static void seedProducts() throws Exception {
        verify();
        execute("DELETE FROM products");
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO products (name, price, stock_qty, expiration_date) VALUES (?, ?, ?, ?)")) {
            String[] names = {"Lucky Me Pancit Canton", "Coke Mismo 8oz", "Expired test item"};
            for (int i = 0; i < names.length; i++) {
                statement.setString(1, names[i]);
                statement.setBigDecimal(2, new java.math.BigDecimal(i == 0 ? "15.00" : "12.00"));
                statement.setInt(3, i == 0 ? 40 : 24);
                statement.setObject(4, LocalDate.now().plusDays(i == 2 ? -1 : 5));
                statement.executeUpdate();
            }
        }
    }
}
