package tindatrack.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Offline tests use invented examples, never the store owner's private CSV. */
class ProductImportFileTest {
    private static final String HEADER = "name,price,stock_qty,expiration_date";

    @Test
    void preservesQuotedNamesAndExactDecimalValues() {
        var row = parse("\"Sample's drink, \"\"large\"\" — blue\",12.50,30,2028-02-29").getFirst();
        assertEquals("Sample's drink, \"large\" — blue", row.name());
        assertEquals(new BigDecimal("12.50"), row.price());
        assertEquals(30, row.stock());
        assertEquals(LocalDate.of(2028, 2, 29), row.expiration());
        assertEquals(List.of(row), parse(ProductImportFile.csv(row)));
    }

    @Test
    void acceptsExplicitNullZeroStockAndSchemaLimits() {
        var row = parse("Sample,99999999.99,2147483647,NULL").getFirst();
        assertNull(row.expiration());
        assertEquals(Integer.MAX_VALUE, row.stock());
        assertEquals(new BigDecimal("99999999.99"), row.price());
        var zero = parse("Sample,1,0,NULL").getFirst();
        assertEquals(0, zero.stock());
        assertEquals(new BigDecimal("1.00"), zero.price());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.234", "1e2", "100000000.00", "NaN", " 2"})
    void rejectsInvalidPrices(String price) {
        assertThrows(IllegalArgumentException.class, () -> parse("Sample," + price + ",1,NULL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "1.5", "2147483648", "1e2", " 2"})
    void rejectsMissingOrInvalidStock(String stock) {
        assertThrows(IllegalArgumentException.class, () -> parse("Sample,1," + stock + ",NULL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "N/A", "null", "2027-02-29", "2028-04-31", "05-29-2028", "2028-1-01"})
    void rejectsMissingOrInvalidDates(String date) {
        assertThrows(IllegalArgumentException.class, () -> parse("Sample,1,1," + date));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sample,1,1", "Sample,1,1,NULL,extra", "\"Sample,1,1,NULL",
            "Sa\"mple,1,1,NULL", "\"Sample\"x,1,1,NULL", ",1,1,NULL", " Sample,1,1,NULL"})
    void rejectsMalformedRows(String line) {
        assertThrows(IllegalArgumentException.class, () -> parse(line));
    }

    @Test
    void rejectsDuplicateNamesIgnoringCaseAndReportsLineNumber() {
        var failure = assertThrows(IllegalArgumentException.class, () ->
                ProductImportFile.parse(List.of(HEADER, "Sample,1,1,NULL", "SAMPLE,2,2,NULL")));
        assertTrue(failure.getMessage().contains("line 3"));
        assertFalse(failure.getMessage().contains("SAMPLE"));
    }

    @Test
    void requiresHeaderAndAtLeastOneProduct() {
        assertThrows(IllegalArgumentException.class, () -> ProductImportFile.parse(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ProductImportFile.parse(List.of(HEADER)));
        assertThrows(IllegalArgumentException.class, () -> ProductImportFile.parse(List.of("name,price")));
        assertEquals(1, ProductImportFile.parse(List.of("\uFEFF" + HEADER, "Sample,1,1,NULL")).size());
    }

    @Test
    void enforcesNameLengthAndRejectsControlCharacters() {
        assertEquals(150, parse("a".repeat(150) + ",1,1,NULL").getFirst().name().length());
        assertThrows(IllegalArgumentException.class, () -> parse("a".repeat(151) + ",1,1,NULL"));
        assertThrows(IllegalArgumentException.class, () -> parse("Sam\tple,1,1,NULL"));
    }

    private static List<ProductImportFile.Row> parse(String row) {
        return ProductImportFile.parse(List.of(HEADER, row));
    }
}
