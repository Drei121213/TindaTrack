package tindatrack.inventory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads the manually reviewed four-column CSV; it never connects to a database. */
public final class ProductImportFile {
    public record Row(String name, BigDecimal price, int stock, LocalDate expiration) { }
    private static final String HEADER = "name,price,stock_qty,expiration_date";

    private ProductImportFile() { }

    public static List<Row> read(Path file) throws IOException {
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    static List<Row> parse(List<String> lines) {
        if (lines.isEmpty() || !lines.getFirst().replace("\uFEFF", "").equals(HEADER)) {
            throw new IllegalArgumentException("Use CSV header: " + HEADER);
        }
        List<Row> rows = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            try {
                List<String> fields = fields(lines.get(i));
                if (fields.size() != 4) throw new IllegalArgumentException("Expected four columns.");
                String name = fields.get(0);
                if (name.isBlank() || !name.equals(name.strip())
                        || name.codePointCount(0, name.length()) > 150
                        || name.codePoints().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("Use a nonblank name of at most 150 characters without surrounding whitespace/control characters.");
                }
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate name in the CSV.");
                String amount = fields.get(1);
                if (!amount.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Use a positive decimal price with at most two decimal places.");
                BigDecimal price = new BigDecimal(amount);
                if (price.signum() <= 0 || price.compareTo(new BigDecimal("99999999.99")) > 0) {
                    throw new IllegalArgumentException("Price must be from 0.01 to 99999999.99.");
                }
                if (!fields.get(2).matches("[0-9]+")) throw new IllegalArgumentException("Stock must be an explicit nonnegative whole number.");
                int stock = Integer.parseInt(fields.get(2));
                String date = fields.get(3);
                if (date.isBlank()) throw new IllegalArgumentException("Use YYYY-MM-DD or explicit NULL; do not leave expiration missing.");
                LocalDate expiration = date.equals("NULL") ? null : ExpirationDates.parseOptional(date);
                rows.add(new Row(name, price.setScale(2), stock, expiration));
            } catch (IllegalArgumentException | java.time.format.DateTimeParseException invalid) {
                // Keep messages useful without echoing private product values.
                throw new IllegalArgumentException("CSV line " + (i + 1) + " is invalid: check its name, price, stock and expiration.", invalid);
            }
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("The CSV contains no products.");
        return List.copyOf(rows);
    }

    /** Single-line CSV with quoted commas and doubled quotes; multiline names are rejected. */
    private static List<String> fields(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; }
                    else { quoted = false; closedQuote = true; }
                } else value.append(c);
            } else if (c == ',') {
                result.add(value.toString()); value.setLength(0); closedQuote = false;
            } else if (c == '"' && value.isEmpty() && !closedQuote) {
                quoted = true;
            } else {
                if (closedQuote || c == '"') throw new IllegalArgumentException("Malformed CSV quoting.");
                value.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote.");
        result.add(value.toString());
        return result;
    }

    public static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String csv(Row row) {
        return csv(row.name()) + "," + row.price().toPlainString() + "," + row.stock() + ","
                + (row.expiration() == null ? "NULL" : row.expiration());
    }
}
