package tindatrack.credit;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import tindatrack.authentication.OwnerSession;
import tindatrack.core.DatabaseConnection;
import tindatrack.core.SessionState;

/** Reads credit data and saves utang for the currently signed-in owner. */
public final class CreditDao {

    private final SessionState sessions;
    private final OwnerSession owner;

    public CreditDao(SessionState sessions, OwnerSession owner) {
        this.sessions = Objects.requireNonNull(sessions);
        this.owner = Objects.requireNonNull(owner);
    }

    /** Also used before displaying results returned by a background task. */
    public void requireOwnerAccess() {
        sessions.requireActive(owner);
    }

    /**
     * Inserts the purchase and updates the balance in one transaction. New names
     * are resolved under an application name lock because the RTM has no UNIQUE
     * customer-name constraint. Selecting an existing ID avoids name ambiguity.
     */
    int recordUtang(UtangRequest request) throws IOException, SQLException {
        requireOwnerAccess();
        UtangRequest validated = CreditRules.validate(request);
        try (Connection connection = DatabaseConnection.getConnection()) {
            if (validated.customerId() == null) {
                lockCustomerNames(connection);
            }
            connection.setAutoCommit(false);
            try {
                requireOwnerAccess();
                LockedCustomer customer = findOrCreateCustomer(connection, validated);
                BigDecimal updatedBalance = CreditRules.addUtang(customer.balance(), validated.amount());
                LocalDateTime transactionDate = validated.date().atTime(LocalTime.now().withNano(0));
                String insertSql = """
                        INSERT INTO credit_transactions (customer_id, items, amount, transaction_date)
                        VALUES (?, ?, ?, ?)
                        """;
                requireOwnerAccess();
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setInt(1, customer.id());
                    insert.setString(2, validated.items());
                    insert.setBigDecimal(3, validated.amount());
                    insert.setObject(4, transactionDate);
                    insert.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE customers SET balance = ? WHERE customer_id = ?")) {
                    update.setBigDecimal(1, updatedBalance);
                    update.setInt(2, customer.id());
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("The customer balance could not be updated.");
                    }
                }
                requireOwnerAccess();
                connection.commit();
                return customer.id();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }

    private LockedCustomer findOrCreateCustomer(Connection connection, UtangRequest request)
            throws SQLException {
        boolean byId = request.customerId() != null;
        String sql = byId
                ? "SELECT customer_id, balance FROM customers WHERE customer_id = ? FOR UPDATE"
                : "SELECT customer_id, balance FROM customers WHERE name = ? ORDER BY customer_id FOR UPDATE";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            if (byId) {
                select.setInt(1, request.customerId());
            } else {
                select.setString(1, request.customerName());
            }
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    LockedCustomer customer = new LockedCustomer(rows.getInt("customer_id"), rows.getBigDecimal("balance"));
                    if (rows.next()) {
                        throw new CreditRules.ValidationException("customer",
                                "Several customers have this name. Select the customer from the list.");
                    }
                    return customer;
                }
            }
        }
        if (byId) {
            throw new CreditRules.ValidationException("customer",
                    "This customer was removed. Cancel and refresh the credit list.");
        }
        requireOwnerAccess();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO customers (name, balance) VALUES (?, 0.00)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, request.customerName());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The new customer's ID was not returned.");
                }
                return new LockedCustomer(keys.getInt(1), new BigDecimal("0.00"));
            }
        }
    }

    private void lockCustomerNames(Connection connection) throws SQLException {
        // The connection closes after commit/rollback, releasing this MySQL lock.
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 3)")) {
            statement.setString(1, "tindatrack.customer-names");
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1) {
                    throw new SQLException("Could not acquire the customer name lock.");
                }
            }
        }
    }

    private record LockedCustomer(int id, BigDecimal balance) {
    }

    /**
     * Finds names containing the literal search text. An empty search lists everyone.
     * Last transaction includes both utang and payments, without changing the schema.
     */
    public List<Customer> findCustomers(String search) throws IOException, SQLException {
        requireOwnerAccess();
        String sql = """
                SELECT c.customer_id, c.name, c.balance, latest.last_transaction
                FROM customers c
                LEFT JOIN (
                    SELECT customer_id, MAX(activity_date) AS last_transaction
                    FROM (
                        SELECT customer_id, transaction_date AS activity_date
                        FROM credit_transactions
                        UNION ALL
                        SELECT customer_id, payment_date AS activity_date FROM payments
                    ) activity
                    GROUP BY customer_id
                ) latest ON latest.customer_id = c.customer_id
                WHERE LOCATE(?, c.name) > 0
                ORDER BY c.name, c.customer_id
                """;
        List<Customer> customers = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, search.strip());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        customers.add(new Customer(rows.getInt("customer_id"), rows.getString("name"),
                                rows.getBigDecimal("balance"),
                                rows.getObject("last_transaction", LocalDateTime.class)));
                    }
                }
            }
        }
        requireOwnerAccess();
        return customers;
    }

    /** Reads both history tables for one customer, with the newest entries first. */
    public List<CreditTransaction> findHistory(int customerId) throws IOException, SQLException {
        requireOwnerAccess();
        String sql = """
                SELECT transaction_id AS entry_id, 'Utang' AS entry_type, items,
                       amount, transaction_date AS entry_date
                FROM credit_transactions WHERE customer_id = ?
                UNION ALL
                SELECT payment_id AS entry_id, 'Payment' AS entry_type, NULL AS items,
                       amount, payment_date AS entry_date
                FROM payments WHERE customer_id = ?
                ORDER BY entry_date DESC, entry_type, entry_id DESC
                """;
        List<CreditTransaction> history = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, customerId);
                statement.setInt(2, customerId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        history.add(new CreditTransaction(rows.getInt("entry_id"),
                                rows.getString("entry_type"), rows.getString("items"),
                                rows.getBigDecimal("amount"),
                                rows.getObject("entry_date", LocalDateTime.class)));
                    }
                }
            }
        }
        requireOwnerAccess();
        return history;
    }
}
