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

class PaymentRulesTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 27);

    @ParameterizedTest
    @CsvSource({"100, 150.00", "50, 200.00", "49.50, 200.50", "250, 0.00"})
    void supportsPartialAndFullPaymentsWithTwoDecimals(String amount, String expected) {
        PaymentRequest payment = CreditRules.preparePayment(7, amount, DATE);
        assertEquals(new BigDecimal(expected),
                CreditRules.subtractPayment(new BigDecimal("250.00"), payment.amount()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0", "0.00", "-10", "abc", "1.001", "100000000",
            "1e2", "1,000.00", "+10", "NaN"})
    void rejectsEmptyZeroNegativeNonnumericAndUnsupportedAmounts(String amount) {
        CreditRules.ValidationException error = assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.preparePayment(7, amount, DATE));
        assertEquals("amount", error.field());
        assertEquals("Please enter a valid amount.", error.getMessage());
    }

    @Test
    void rejectsOverpaymentAndReportsTheCurrentBalance() {
        CreditRules.PaymentBalanceException error = assertThrows(CreditRules.PaymentBalanceException.class,
                () -> CreditRules.subtractPayment(new BigDecimal("150.00"), new BigDecimal("200.00")));
        assertEquals("Payment cannot be more than the balance.", error.getMessage());
        assertEquals(new BigDecimal("150.00"), error.balance());
    }

    @Test
    void rejectsCustomersWithoutBalance() {
        CreditRules.PaymentBalanceException error = assertThrows(CreditRules.PaymentBalanceException.class,
                () -> CreditRules.requireOutstandingBalance(new BigDecimal("0.00")));
        assertEquals("This customer has no balance.", error.getMessage());
    }

    @Test
    void partialThenFullPaymentEndsAtZeroAndRejectsAnotherPayment() {
        BigDecimal balance = CreditRules.subtractPayment(new BigDecimal("250.00"), new BigDecimal("100.00"));
        assertEquals(new BigDecimal("150.00"), balance);
        BigDecimal remaining = CreditRules.subtractPayment(balance, new BigDecimal("150.00"));
        assertEquals(new BigDecimal("0.00"), remaining);
        assertThrows(CreditRules.PaymentBalanceException.class,
                () -> CreditRules.subtractPayment(remaining, new BigDecimal("0.01")));
    }

    @Test
    void validatesAgainstTheNewerBalanceRatherThanAnOldPreview() {
        PaymentRequest request = CreditRules.preparePayment(7, "100.00", DATE);
        assertEquals(new BigDecimal("50.00"),
                CreditRules.subtractPayment(new BigDecimal("150.00"), request.amount()));
        CreditRules.PaymentBalanceException error = assertThrows(CreditRules.PaymentBalanceException.class,
                () -> CreditRules.subtractPayment(new BigDecimal("50.00"), request.amount()));
        assertEquals(new BigDecimal("50.00"), error.balance());
    }

    @Test
    void keepsDecimalArithmeticExact() {
        assertEquals(new BigDecimal("0.10"),
                CreditRules.subtractPayment(new BigDecimal("0.30"), new BigDecimal("0.20")));
    }

    @Test
    void acceptsEarlierDatesButRequiresAnExistingCustomerAndValidSqlDate() {
        assertEquals(DATE.minusDays(1), CreditRules.preparePayment(7, "10", DATE.minusDays(1)).date());
        assertThrows(CreditRules.ValidationException.class, () -> CreditRules.preparePayment(0, "10", DATE));
        for (LocalDate invalid : new LocalDate[] {null, LocalDate.of(999, 12, 31), LocalDate.of(10000, 1, 1)}) {
            assertThrows(CreditRules.ValidationException.class,
                    () -> CreditRules.preparePayment(7, "10", invalid));
        }
    }

    @Test
    void revalidatesRequestsThatBypassThePaymentForm() {
        assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.validate(new PaymentRequest(7, new BigDecimal("-10.00"), DATE)));
        assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.validate(new PaymentRequest(7, new BigDecimal("1.001"), DATE)));
        assertThrows(CreditRules.ValidationException.class,
                () -> CreditRules.validate(new PaymentRequest(7, null, DATE)));
        assertThrows(CreditRules.ValidationException.class, () -> CreditRules.validate((PaymentRequest) null));
    }

    @Test
    void serviceAndDaoRejectGuestAndInactiveOwnerPaymentsBeforeDatabaseAccess() {
        SessionState sessions = new SessionState();
        OwnerSession owner = new OwnerSession(1, "Owner");
        CreditDao dao = new CreditDao(sessions, owner);
        CreditService service = new CreditService(dao);
        PaymentRequest payment = CreditRules.preparePayment(7, "50", DATE);
        assertPaymentDenied(service, dao, payment);
        sessions.begin(new GuestSession());
        assertPaymentDenied(service, dao, payment);
        sessions.clear();
        sessions.begin(new OwnerSession(1, "Owner"));
        assertPaymentDenied(service, dao, payment);
        sessions.clear();
        sessions.begin(owner);
        assertEquals(new BigDecimal("200.00"), service.previewPayment(new BigDecimal("250.00"), "50"));
        sessions.clear();
        assertPaymentDenied(service, dao, payment);
    }

    private void assertPaymentDenied(CreditService service, CreditDao dao, PaymentRequest payment) {
        assertThrows(SessionState.AccessDeniedException.class, () -> service.recordPayment(payment));
        assertThrows(SessionState.AccessDeniedException.class, () -> dao.recordPayment(payment));
        assertThrows(SessionState.AccessDeniedException.class,
                () -> service.previewPayment(new BigDecimal("250.00"), "50"));
        assertThrows(SessionState.AccessDeniedException.class, () -> dao.findCustomer(7));
    }
}
