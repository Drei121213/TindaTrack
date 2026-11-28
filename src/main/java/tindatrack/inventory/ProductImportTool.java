package tindatrack.inventory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import tindatrack.core.DatabaseConnection;
import tindatrack.inventory.ProductImportFile.Row;

/** Manually invoked maintenance tool. The normal JavaFX application never calls it. */
public final class ProductImportTool {
    private record Input(List<Row> rows, String sha256) { }
    private record Decision(Row requested, List<Product> existing) { }
    private record Plan(String text, List<Decision> decisions, int newCount, int skipCount) { }

    private ProductImportTool() { }

    public static void main(String[] args) {
        try {
            if (args.length == 2 && args[0].equals("validate")) {
                Input input = read(privateFile(args[1], true));
                System.out.println("CSV valid: " + input.rows().size() + " products; "
                        + input.rows().stream().filter(r -> r.expiration() == null).count() + " NULL expiration dates.");
                System.out.println("CSV SHA-256: " + input.sha256());
                System.out.println("No database connection was opened.");
            } else if (args.length == 3 && args[0].equals("plan")) {
                Input input = read(privateFile(args[1], true));
                Path output = privateFile(args[2], false);
                try (Connection connection = DatabaseConnection.getConnection()) {
                    connection.setReadOnly(true);
                    Plan plan = plan(connection, input, false);
                    journal(output, plan.text(), true);
                    System.out.println("Read-only plan saved. New: " + plan.newCount() + "; skipped: " + plan.skipCount() + ".");
                    System.out.println("Review the target server/database and every existing match in that private plan.");
                }
            } else if (args.length == 6 && args[0].equals("apply") && args[5].equals("--approved")) {
                Input input = read(privateFile(args[1], true));
                Path planFile = privateFile(args[2], true);
                Path backup = privateFile(args[3], true);
                Path receipt = privateFile(args[4], false);
                if (Files.size(backup) == 0) throw new IllegalArgumentException("The reviewed backup file is empty.");
                apply(input, Files.readString(planFile, StandardCharsets.UTF_8), backup, receipt);
            } else {
                throw new IllegalArgumentException("Use validate CSV; plan CSV PLAN; or, only after separate import approval, apply CSV PLAN BACKUP RECEIPT --approved.");
            }
        } catch (ImportFailure failure) {
            System.err.println(failure.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException invalid) {
            System.err.println(invalid.getMessage());
            System.exit(1);
        } catch (SQLException failure) {
            System.err.println("Database operation failed. No import success is claimed. Check the reviewed target and connection configuration.");
            System.exit(1);
        } catch (IOException failure) {
            System.err.println("Cannot read or safely create the required private files. Existing output files are never overwritten.");
            System.exit(1);
        }
    }

    /** Restricts private input/output to this project's real private-import directory. */
    private static Path privateFile(String argument, boolean existing) throws IOException {
        Path project = Path.of("").toRealPath();
        if (!Files.isRegularFile(project.resolve("pom.xml"))) throw new IOException("Run from the project root.");
        Path root = project.resolve("private-import").toRealPath();
        if (!root.startsWith(project)) throw new IOException("Private directory must stay in the project.");
        Path file = Path.of(argument).toAbsolutePath().normalize();
        if (existing) {
            file = file.toRealPath();
            if (!Files.isRegularFile(file) || !file.startsWith(root)) throw new IOException("Use a private project file.");
        } else {
            if (Files.exists(file) || !file.getParent().toRealPath().startsWith(root)) throw new IOException("Use a new private output file.");
        }
        return file;
    }

    private static Input read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        return new Input(ProductImportFile.parse(text.lines().toList()), hash(bytes));
    }

    private static Plan plan(Connection connection, Input input, boolean lockRows) throws SQLException {
        StringBuilder text = new StringBuilder("TindaTrack product import plan v1\nCSV-SHA256=" + input.sha256() + "\n");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DATABASE(), @@server_uuid, @@hostname, @@port"); ResultSet row = statement.executeQuery()) {
            if (!row.next() || row.getString(1) == null) throw new SQLException("A target database is required.");
            text.append("DATABASE=").append(row.getString(1)).append("\nSERVER-UUID=").append(row.getString(2))
                    .append("\nHOST=").append(row.getString(3)).append("\nPORT=").append(row.getInt(4)).append('\n');
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='products'");
             ResultSet row = statement.executeQuery()) {
            if (!row.next() || !"InnoDB".equalsIgnoreCase(row.getString(1))) throw new SQLException("Transactional InnoDB products table required.");
        }
        text.append("ENGINE=InnoDB\n\nActions and requested data (name,price,stock_qty,expiration_date):\n");
        List<Decision> decisions = new ArrayList<>();
        int added = 0;
        for (Row requested : input.rows()) {
            List<Product> matches = matches(connection, requested.name(), lockRows);
            decisions.add(new Decision(requested, matches));
            text.append(matches.isEmpty() ? "INSERT " : "SKIP ").append(ProductImportFile.csv(requested)).append('\n');
            if (matches.isEmpty()) added++;
            for (Product match : matches) {
                text.append("  Existing ID=").append(match.id()).append(' ')
                        .append(ProductImportFile.csv(new Row(match.name(), match.price(), match.stockQuantity(), match.expirationDate()))).append('\n');
            }
        }
        int skipped = input.rows().size() - added;
        text.append("\nNEW=").append(added).append("\nSKIPPED=").append(skipped).append('\n');
        return new Plan(text.toString(), List.copyOf(decisions), added, skipped);
    }

    private static List<Product> matches(Connection connection, String name, boolean locking) throws SQLException {
        String sql = "SELECT product_id,name,price,stock_qty,expiration_date FROM products WHERE name=? ORDER BY product_id"
                + (locking ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                List<Product> result = new ArrayList<>();
                while (rows.next()) result.add(product(rows));
                return List.copyOf(result);
            }
        }
    }

    private static Product product(ResultSet row) throws SQLException {
        return new Product(row.getInt("product_id"), row.getString("name"), row.getBigDecimal("price"),
                row.getInt("stock_qty"), row.getObject("expiration_date", LocalDate.class));
    }

    private static void apply(Input input, String approvedPlan, Path backup, Path receipt) throws SQLException, IOException, ImportFailure {
        String backupHash = hashFile(backup);
        boolean receiptCreated = false;
        boolean commitAttempted = false;
        boolean committed = false;
        try (Connection connection = DatabaseConnection.getConnection()) {
            // Same name lock as ProductDao.addProduct; closing this connection releases it.
            try (PreparedStatement lock = connection.prepareStatement("SELECT GET_LOCK(?,3)")) {
                lock.setString(1, "tindatrack.product-names");
                try (ResultSet row = lock.executeQuery()) {
                    if (!row.next() || row.getInt(1) != 1) throw new SQLException("Product-name lock unavailable.");
                }
            }
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                Plan current = plan(connection, input, true);
                if (!current.text().equals(approvedPlan)) {
                    throw new IllegalArgumentException("CSV, target or existing matches changed. Prepare and approve a new plan before importing.");
                }
                journal(receipt, "STATUS=PENDING\nBACKUP-SHA256=" + backupHash + "\n" + approvedPlan
                        + "\nNewly inserted records only (ID,name,price,stock_qty,expiration_date):\n", true);
                receiptCreated = true;
                StringBuilder inserted = new StringBuilder();
                for (Decision decision : current.decisions()) {
                    if (!decision.existing().isEmpty()) continue;
                    Row row = decision.requested();
                    // Also prevents differently spelled CSV names that collide under MySQL collation.
                    if (!matches(connection, row.name(), true).isEmpty()) {
                        throw new IllegalArgumentException("Two CSV names match under database collation. Revise the list; no duplicate is inserted.");
                    }
                    int id = insert(connection, row);
                    verify(connection, id, row);
                    inserted.append(id).append(',').append(ProductImportFile.csv(row)).append('\n');
                }
                // Flush all new IDs/snapshots before committing; do not rely on a post-commit file write.
                journal(receipt, inserted + "STATUS=PRECOMMIT\n", false);
                commitAttempted = true;
                connection.commit();
                committed = true;
                journal(receipt, "STATUS=COMMITTED\n", false);
                System.out.println("Import committed. Inserted: " + current.newCount() + "; skipped: " + current.skipCount() + ".");
                System.out.println("Keep the private receipt and reviewed backup. Existing products were not updated or deleted.");
            } catch (SQLException | IOException | RuntimeException problem) {
                String state;
                if (committed) state = "COMMITTED_RECEIPT_STATUS_INCOMPLETE";
                else if (commitAttempted) state = "COMMIT_OUTCOME_UNCERTAIN";
                else state = "ROLLBACK_UNCONFIRMED";
                if (!committed) {
                    try {
                        connection.rollback();
                        if (!commitAttempted) state = "ROLLED_BACK";
                    } catch (SQLException rollbackFailure) { problem.addSuppressed(rollbackFailure); }
                }
                if (receiptCreated) {
                    try { journal(receipt, "STATUS=" + state + "\n", false); }
                    catch (IOException journalFailure) { problem.addSuppressed(journalFailure); }
                }
                String message = state.equals("ROLLED_BACK") ? "Import was not committed; the transaction was rolled back."
                        : committed ? "The database commit succeeded, but receipt finalization failed. Do not repeat the import; inspect its recorded IDs."
                        : "The import outcome could not be confirmed. Do not retry or delete records; review the private receipt and database first.";
                if (problem instanceof IllegalArgumentException) message += " " + problem.getMessage();
                throw new ImportFailure(message, problem);
            }
        } catch (SQLException closeOrSetupFailure) {
            if (committed) {
                throw new ImportFailure("The database commit succeeded, but closing the connection failed. Check the receipt; do not repeat the import.", closeOrSetupFailure);
            }
            throw closeOrSetupFailure;
        }
    }

    private static int insert(Connection connection, Row row) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO products(name,price,stock_qty,expiration_date) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, row.name());
            statement.setBigDecimal(2, row.price());
            statement.setInt(3, row.stock());
            if (row.expiration() == null) statement.setNull(4, Types.DATE);
            else statement.setObject(4, row.expiration(), Types.DATE);
            if (statement.executeUpdate() != 1) throw new SQLException("One inserted product was expected.");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next() || keys.getInt(1) <= 0) throw new SQLException("The inserted ID was not returned.");
                return keys.getInt(1);
            }
        }
    }

    private static void verify(Connection connection, int id, Row expected) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT product_id,name,price,stock_qty,expiration_date FROM products WHERE product_id=?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Inserted product missing.");
                Product actual = product(rows);
                if (!actual.name().equals(expected.name()) || actual.price().compareTo(expected.price()) != 0
                        || actual.stockQuantity() != expected.stock() || !Objects.equals(actual.expirationDate(), expected.expiration())) {
                    throw new SQLException("Inserted data did not match the approved row.");
                }
            }
        }
    }

    private static void journal(Path path, String text, boolean create) throws IOException {
        try (FileChannel file = create ? FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
                : FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
    }

    private static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static String hashFile(Path path) throws IOException {
        MessageDigest digest = digest();
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = stream.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable.", impossible); }
    }

    private static final class ImportFailure extends Exception {
        ImportFailure(String message, Throwable cause) { super(message, cause); }
    }
}
