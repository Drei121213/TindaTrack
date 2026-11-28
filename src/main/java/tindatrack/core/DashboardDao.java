package tindatrack.core;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import tindatrack.authentication.OwnerSession;

/** Reads SB02 totals from existing tables; it never changes store records. */
public final class DashboardDao {
    private final SessionState sessions;
    private final OwnerSession owner;

    public DashboardDao(SessionState sessions, OwnerSession owner) {
        this.sessions = sessions;
        this.owner = owner;
    }

    public void requireOwnerAccess() {
        sessions.requireActive(owner);
    }

    /** One statement reads all totals together, avoiding joins that multiply rows. */
    public DashboardSummary readSummary(LocalDate today) throws IOException, SQLException {
        requireOwnerAccess();
        LocalDate cutoff = today.plusDays(7);
        String sql = """
                SELECT (SELECT COUNT(*) FROM products) AS total_products,
                       (SELECT COALESCE(SUM(balance), 0.00) FROM customers) AS total_credits,
                       (SELECT COUNT(*) FROM products
                        WHERE expiration_date IS NOT NULL AND expiration_date <= ?) AS expiring_products
                """;
        DashboardSummary summary;
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, cutoff, java.sql.Types.DATE);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) throw new SQLException("The dashboard summary was not returned.");
                    summary = new DashboardSummary(row.getLong("total_products"), row.getBigDecimal("total_credits"),
                            row.getLong("expiring_products"), cutoff);
                }
            }
        }
        requireOwnerAccess();
        return summary;
    }
}
