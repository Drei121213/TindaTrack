package tindatrack.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ExpirationDatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Test
    void parsesLeapDayAndTrimsOuterWhitespace() {
        assertEquals(LocalDate.of(2028, 2, 29), ExpirationDates.parseOptional(" 2028-02-29 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankDateMeansNoExpiration(String input) {
        assertNull(ExpirationDates.parseOptional(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-45", "2027-02-29", "2026-04-31", "2026-1-01",
                            "11/17/2026", "0999-12-31", "10000-01-01"})
    void rejectsImpossibleDatesAndInvalidFormats(String input) {
        assertThrows(DateTimeParseException.class, () -> ExpirationDates.parseOptional(input));
    }

    @Test
    void confirmsOnlyNewPastDates() {
        LocalDate yesterday = TODAY.minusDays(1);
        assertTrue(ExpirationDates.requiresConfirmation(null, yesterday, TODAY));
        assertTrue(ExpirationDates.requiresConfirmation(TODAY, yesterday, TODAY));
        assertFalse(ExpirationDates.requiresConfirmation(yesterday, yesterday, TODAY));
        assertFalse(ExpirationDates.requiresConfirmation(yesterday, TODAY, TODAY));
        assertFalse(ExpirationDates.requiresConfirmation(yesterday, TODAY.plusDays(1), TODAY));
        assertFalse(ExpirationDates.requiresConfirmation(yesterday, null, TODAY));
    }

    @ParameterizedTest
    @CsvSource({"-1, Expired", "0, Expires Today", "1, 1 Day Left", "5, 5 Days Left"})
    void describesDaysRelativeToTheSameReferenceDate(int days, String expected) {
        assertEquals(expected, ExpirationDates.status(TODAY.plusDays(days), TODAY));
    }
}
