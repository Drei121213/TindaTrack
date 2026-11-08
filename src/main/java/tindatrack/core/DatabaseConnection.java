package tindatrack.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens MySQL connections using local configuration, as specified by RTM Core.
 */
public final class DatabaseConnection {

    private static final Path CONFIG_PATH = Path.of("config", "database.properties");

    private DatabaseConnection() {
        // This utility class is used through its static getConnection method.
    }

    /**
     * Opens a new connection. The caller must close it, preferably using
     * try-with-resources. Run from the folder containing pom.xml.
     */
    public static Connection getConnection() throws IOException, SQLException {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            throw new IOException("Create config/database.properties from the example first.");
        }

        Properties config = new Properties();
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            config.load(reader);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid properties format in config/database.properties.");
        }

        String url = requiredProperty(config, "db.url").trim();
        String username = requiredProperty(config, "db.username").trim();
        String password = requiredProperty(config, "db.password");

        if (!url.startsWith("jdbc:mysql://")) {
            throw new IOException("db.url must begin with jdbc:mysql://.");
        }

        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", username);
        connectionProperties.setProperty("password", password);
        connectionProperties.setProperty("connectTimeout", "5000");
        connectionProperties.setProperty("socketTimeout", "5000");

        return DriverManager.getConnection(url, connectionProperties);
    }

    private static String requiredProperty(Properties config, String key) throws IOException {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Set " + key + " in config/database.properties.");
        }
        return value;
    }
}
