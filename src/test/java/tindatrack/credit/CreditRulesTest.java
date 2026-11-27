package tindatrack.credit;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tindatrack.authentication.OwnerSession;
import tindatrack.core.SessionState;
import tindatrack.guest.GuestSession;

import static org.junit.jupiter.api.Assertions.*;

class CreditRulesTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 27);

    private UtangRequest prepare(String amount) {
        return CreditRules.prepareUtang(7, "Aling Nena", "2 Pancit Canton", amount, DATE);
    }

    @ParameterizedTest
    @CsvSource({"30, 30.00", ".5, 0.50", "49.50, 49.50", "99999999.99, 99999999.99"})
    void acceptsPositiveMoneyWithTwoDecimalPlaces(String input, String expected) {
        assertEquals(new BigDecimal(expected), prepare(input).amount());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0", "0.00", "-10", "abc", "0.001", "30.999",
            "100000000", "1e2", "1,000.00", "NaN", "+3"})
    void rejectsInvalidCreditAmounts(String input) {
        CreditRules.ValidationException error = assertThrows(CreditRules.ValidationException.class,
                () -> prepare(input));
        assertEquals("amount", error.field());
        assertEquals("Please enter a valid amount.", error.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingCustomerNames(String input) {
        CreditRules.ValidationException error = assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.prepareUtang(null, input, "", "30", DATE));
        assertEquals("customer", error.field());
    }

    @Test
    void allowsNewCustomerAndOptionalPlainTextItems() {
        UtangRequest request = CreditRules.prepareUtang(null, "  Aling Rosa  ", null, " 45 ", DATE);
        assertNull(request.customerId());
        assertEquals("Aling Rosa", request.customerName());
        assertEquals("", request.items());
        assertEquals(new BigDecimal("45.00"), request.amount());
    }

    @Test
    void respectsCustomerNameAndIdLimits() {
        assertDoesNotThrow(() -> CreditRules.prepareUtang(null, "a".repeat(100), "", "1", DATE));
        assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.prepareUtang(null, "a".repeat(101), "", "1", DATE));
        assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.prepareUtang(0, "Customer", "", "1", DATE));
    }

    @Test
    void requiresADateWithinTheSqlDateRange() {
        for (LocalDate invalid : new LocalDate[] {null, LocalDate.of(999, 12, 31), LocalDate.of(10000, 1, 1)}) {
            CreditRules.ValidationException error = assertThrows(CreditRules.ValidationException.class,
                    () -> CreditRules.prepareUtang(null, "Customer", "", "1", invalid));
            assertEquals("date", error.field());
        }
    }

    @Test
    void retainsEarlierDatesAndPlainTextItems() {
        UtangRequest request = CreditRules.prepareUtang(7, "Customer", "  Rice\n1 Coke  ", "30", DATE.minusDays(2));
        assertEquals(DATE.minusDays(2), request.date());
        assertEquals("Rice\n1 Coke", request.items());
    }

    @Test
    void checksUtf8TextColumnLimit() {
        String items = "₱".repeat(21845); // Three UTF-8 bytes each: exactly 65535 bytes.
        assertDoesNotThrow(() -> CreditRules.prepareUtang(7, "Customer", items, "1", DATE));
        CreditRules.ValidationException error = assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.prepareUtang(7, "Customer", items + "x", "1", DATE));
        assertEquals("items", error.field());
    }

    @Test
    void revalidatesRequestsThatBypassTheForm() {
        assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.validate(new UtangRequest(7, "Customer", "", new BigDecimal("1.001"), DATE)));
        assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.validate(new UtangRequest(7, "Customer", "", null, DATE)));
    }

    @Test
    void addsUtangToExistingBalanceExactly() {
        assertEquals(new BigDecimal("280.00"), CreditRules.addUtang(new BigDecimal("250.00"), prepare("30").amount()));
        assertEquals(new BigDecimal("0.30"), CreditRules.addUtang(new BigDecimal("0.10"), prepare("0.20").amount()));
    }

    @Test
    void rejectsBalanceOverflowWithoutRounding() {
        assertEquals(new BigDecimal("99999999.99"),
                CreditRules.addUtang(new BigDecimal("99999999.98"), prepare("0.01").amount()));
        CreditRules.ValidationException error = assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.addUtang(new BigDecimal("99999999.99"), prepare("0.01").amount()));
        assertEquals("amount", error.field());
    }

    @Test
    void serviceAndDaoRejectGuestAndInactiveOwnerSavesBeforeDatabaseAccess() {
        SessionState sessions = new SessionState();
        OwnerSession owner = new OwnerSession(1, "Owner");
        CreditDao dao = new CreditDao(sessions, owner);
        CreditService service = new CreditService(dao);
        UtangRequest request = prepare("30");

        assertSaveDenied(service, dao, request);
        sessions.begin(new GuestSession());
        assertSaveDenied(service, dao, request);
        sessions.clear();
        sessions.begin(new OwnerSession(1, "Owner"));
        assertSaveDenied(service, dao, request);
        sessions.clear();
        sessions.begin(owner);
        assertDoesNotThrow(() -> service.prepareUtang(7, "Customer", "", "30", DATE));
        sessions.clear();
        assertSaveDenied(service, dao, request);
    }

    private void assertSaveDenied(CreditService service, CreditDao dao, UtangRequest request) {
        assertThrows(SessionState.AccessDeniedException.class, () -> service.recordUtang(request));
        assertThrows(SessionState.AccessDeniedException.class, () -> dao.recordUtang(request));
    }
}
