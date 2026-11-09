package tindatrack.guest;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import tindatrack.core.DatabaseConnection;
import tindatrack.core.SessionState;

/** Supplies the product and credit columns permitted by UC10. */
public final class GuestService {

    public record ProductRow(String name, BigDecimal price, int stockQuantity) {
    }

    public record BalanceRow(String customerName, BigDecimal balance) {
    }

    private final SessionState sessions;
    private final GuestSession guest;

    public GuestService(SessionState sessions, GuestSession guest) {
        this.sessions = sessions;
        this.guest = guest;
    }

    public List<ProductRow> findProducts(String search) throws IOException, SQLException {
        String sql = """
                SELECT name, price, stock_qty
                FROM products
                WHERE LOCATE(?, name) > 0
                ORDER BY name, product_id
                """;
        List<ProductRow> products = new ArrayList<>();
        try (Connection connection = openReadConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, search.strip());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    products.add(new ProductRow(rows.getString("name"),
                            rows.getBigDecimal("price"), rows.getInt("stock_qty")));
                }
            }
        }
        return products;
    }

    public List<BalanceRow> findBalances() throws IOException, SQLException {
        String sql = "SELECT name, balance FROM customers ORDER BY name, customer_id";
        List<BalanceRow> balances = new ArrayList<>();
        try (Connection connection = openReadConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                balances.add(new BalanceRow(rows.getString("name"), rows.getBigDecimal("balance")));
            }
        }
        return balances;
    }

    private Connection openReadConnection() throws IOException, SQLException {
        sessions.requireActive(guest);
        Connection connection = DatabaseConnection.getConnection();
        try {
            connection.setReadOnly(true);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }
}
