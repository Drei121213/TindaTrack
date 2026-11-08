package tindatrack.core;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manual connection check. Runs SELECT 1 without changing database records.
 */
public class DatabaseConnectionTest {

    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {

            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("Unexpected connection check result.");
                }
                System.out.println("Database connection successful: SELECT 1 returned 1.");
            }
        } catch (IOException exception) {
            // These messages describe configuration, without showing its values.
            System.err.println("Configuration error: " + exception.getMessage());
            System.exit(1);
        } catch (SQLException exception) {
            printDatabaseError(exception);
            System.exit(1);
        }
    }

    private static void printDatabaseError(SQLException exception) {
        String message;
        if (exception.getErrorCode() == 1049) {
            message = "Database not found. Run the Task 3 schema and check db.url.";
        } else if (exception.getErrorCode() == 1045 || exception.getErrorCode() == 1044
                || "28000".equals(exception.getSQLState())) {
            message = "Access denied. Check your MySQL username, password, and database permissions.";
        } else if (exception.getSQLState() != null && exception.getSQLState().startsWith("08")) {
            message = "Cannot connect. Check MySQL Server, the host/port, and any TLS configuration.";
        } else {
            message = "The connection check failed. Check the server and connection configuration.";
        }

        System.err.println("Database error: " + message);
        // Avoid printing the driver's raw message, which can include connection details.
        System.err.println("SQLState: " + exception.getSQLState()
                + "; error code: " + exception.getErrorCode());
    }
}
