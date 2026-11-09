package tindatrack.authentication;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import tindatrack.core.DatabaseConnection;

/** Reads and creates owner accounts in MySQL. */
public class UserDao {

    public enum CreateResult {
        CREATED, USERNAME_TAKEN, OWNER_EXISTS
    }

    /** The hash is needed only while checking credentials. */
    record LoginAccount(int userId, String fullName, String passwordHash) {
    }

    Optional<LoginAccount> findByUsername(String username) throws IOException, SQLException {
        String sql = "SELECT user_id, full_name, password_hash FROM users WHERE username = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) {
                    return Optional.of(new LoginAccount(
                            row.getInt("user_id"), row.getString("full_name"),
                            row.getString("password_hash")));
                }
                return Optional.empty();
            }
        }
    }

    public boolean hasOwner() throws IOException, SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users LIMIT 1");
             ResultSet rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    public CreateResult createOwner(String fullName, String username, String passwordHash)
            throws IOException, SQLException {
        // MySQL releases this registration lock when this connection closes.
        try (Connection connection = DatabaseConnection.getConnection()) {
            lockOwnerRegistration(connection);

            String checkSql = """
                    SELECT EXISTS(SELECT 1 FROM users WHERE username = ?) AS username_taken,
                           EXISTS(SELECT 1 FROM users) AS owner_exists
                    """;
            try (PreparedStatement check = connection.prepareStatement(checkSql)) {
                check.setString(1, username);
                try (ResultSet result = check.executeQuery()) {
                    result.next();
                    if (result.getBoolean("username_taken")) {
                        return CreateResult.USERNAME_TAKEN;
                    }
                    if (result.getBoolean("owner_exists")) {
                        return CreateResult.OWNER_EXISTS;
                    }
                }
            }

            String insertSql = "INSERT INTO users (full_name, username, password_hash) VALUES (?, ?, ?)";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setString(1, fullName);
                insert.setString(2, username);
                insert.setString(3, passwordHash);
                insert.executeUpdate();
                return CreateResult.CREATED;
            } catch (SQLIntegrityConstraintViolationException exception) {
                if (exception.getErrorCode() == 1062) { // MySQL duplicate key
                    return CreateResult.USERNAME_TAKEN;
                }
                throw exception;
            }
        }
    }

    private void lockOwnerRegistration(Connection connection) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT GET_LOCK(?, 3)")) {
            lock.setString(1, "tindatrack.owner-registration");
            try (ResultSet result = lock.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("Owner registration lock was unavailable.");
                }
            }
        }
    }
}
