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

/** Reads and changes products for the active owner. */
public final class ProductDao {

    public enum AddResult { ADDED, DUPLICATE_NAME }
    public enum DeleteResult { DELETED, STALE, REFERENCED }

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
                bindDate(insert, 4, expirationDate);
                insert.executeUpdate();
                return AddResult.ADDED;
            }
        }
    }

    public boolean updateProduct(Product original, BigDecimal price, int stockQuantity,
                                 LocalDate expirationDate) throws IOException, SQLException {
        sessions.requireActive(owner);
        String sql = """
                UPDATE products SET price = ?, stock_qty = ?, expiration_date = ?
                WHERE product_id = ? AND name = ? AND price = ? AND stock_qty = ?
                    AND expiration_date <=> ?
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, price);
            statement.setInt(2, stockQuantity);
            bindDate(statement, 3, expirationDate);
            bindOriginal(statement, 4, original);
            sessions.requireActive(owner);
            return statement.executeUpdate() == 1;
        }
    }

    public DeleteResult deleteProduct(Product original) throws IOException, SQLException {
        sessions.requireActive(owner);
        String sql = """
                DELETE FROM products
                WHERE product_id = ? AND name = ? AND price = ? AND stock_qty = ?
                    AND expiration_date <=> ?
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOriginal(statement, 1, original);
            sessions.requireActive(owner);
            return statement.executeUpdate() == 1 ? DeleteResult.DELETED : DeleteResult.STALE;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1451 || exception.getErrorCode() == 1217) {
                return DeleteResult.REFERENCED;
            }
            throw exception;
        }
    }

    private void bindOriginal(PreparedStatement statement, int start, Product original)
            throws SQLException {
        statement.setInt(start, original.id());
        statement.setString(start + 1, original.name());
        statement.setBigDecimal(start + 2, original.price());
        statement.setInt(start + 3, original.stockQuantity());
        bindDate(statement, start + 4, original.expirationDate());
    }

    private void bindDate(PreparedStatement statement, int parameter, LocalDate date)
            throws SQLException {
        if (date == null) {
            statement.setNull(parameter, Types.DATE);
        } else {
            statement.setObject(parameter, date, Types.DATE);
        }
    }

    public List<Product> findAll() throws IOException, SQLException {
        return findByName("");
    }

    public List<Product> findByName(String name) throws IOException, SQLException {
        sessions.requireActive(owner);
        String sql = """
                SELECT product_id, name, price, stock_qty, expiration_date
                FROM products
                WHERE LOCATE(?, name) > 0
                ORDER BY name, product_id
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.strip());
            return readProducts(statement);
        }
    }

    /** Finds expired products and products due on or before the cutoff. */
    public List<Product> findExpiringThrough(LocalDate cutoff) throws IOException, SQLException {
        sessions.requireActive(owner);
        String sql = """
                SELECT product_id, name, price, stock_qty, expiration_date
                FROM products
                WHERE expiration_date IS NOT NULL AND expiration_date <= ?
                ORDER BY expiration_date, name, product_id
                """;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, cutoff, Types.DATE);
            return readProducts(statement);
        }
    }

    private List<Product> readProducts(PreparedStatement statement) throws SQLException {
        List<Product> products = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery()) {
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
