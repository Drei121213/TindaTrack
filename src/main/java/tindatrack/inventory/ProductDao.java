package tindatrack.inventory;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import tindatrack.authentication.OwnerSession;
import tindatrack.core.DatabaseConnection;
import tindatrack.core.SessionState;

/** Reads and adds products for the active owner. */
public final class ProductDao {

    public enum AddResult { ADDED, DUPLICATE_NAME }

    private final SessionState sessions;
    private final OwnerSession owner;

    public ProductDao(SessionState sessions, OwnerSession owner) {
        this.sessions = sessions;
        this.owner = owner;
    }

    public AddResult addProduct(String name, BigDecimal price, int stockQuantity,
                                LocalDate expirationDate) throws IOException, SQLException {
        sessions.requireActive(owner);
        try (Connection connection = DatabaseConnection.getConnection()) {
            lockProductNames(connection);
            sessions.requireActive(owner);
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM products WHERE name = ? LIMIT 1")) {
                check.setString(1, name);
                try (ResultSet matches = check.executeQuery()) {
                    if (matches.next()) {
                        return AddResult.DUPLICATE_NAME;
                    }
                }
            }

            String sql = "INSERT INTO products (name, price, stock_qty, expiration_date) VALUES (?, ?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(sql)) {
                sessions.requireActive(owner);
                insert.setString(1, name);
                insert.setBigDecimal(2, price);
                insert.setInt(3, stockQuantity);
                if (expirationDate == null) {
                    insert.setNull(4, Types.DATE);
                } else {
                    insert.setObject(4, expirationDate, Types.DATE);
                }
                insert.executeUpdate();
                return AddResult.ADDED;
            }
        }
    }

    public List<Product> findAll() throws IOException, SQLException {
        sessions.requireActive(owner);
        String sql = "SELECT product_id, name, price, stock_qty, expiration_date "
                + "FROM products ORDER BY name, product_id";
        List<Product> products = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                products.add(new Product(rows.getInt("product_id"), rows.getString("name"),
                        rows.getBigDecimal("price"), rows.getInt("stock_qty"),
                        rows.getObject("expiration_date", LocalDate.class)));
            }
        }
        return products;
    }

    private void lockProductNames(Connection connection) throws SQLException {
        // Closing the connection releases the lock. It covers the name check and insert.
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 3)")) {
            statement.setString(1, "tindatrack.product-names");
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("Could not acquire the product name lock.");
                }
            }
        }
    }
}
