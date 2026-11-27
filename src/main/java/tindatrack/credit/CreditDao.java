package tindatrack.credit;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import tindatrack.authentication.OwnerSession;
import tindatrack.core.DatabaseConnection;
import tindatrack.core.SessionState;

/** Reads customer balances and history for the currently signed-in owner. */
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
