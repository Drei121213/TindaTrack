package tindatrack.credit;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Validation and money calculations shared by the form and save operation. */
final class CreditRules {

    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");

    private CreditRules() {
    }

    static UtangRequest prepareUtang(Integer customerId, String name, String items,
                                      String amountText, LocalDate date) {
        String customerName = name == null ? "" : name.strip();
        if (customerName.isEmpty()) {
            throw new ValidationException("customer", "Enter or select a customer name.");
        }
        if (customerName.codePointCount(0, customerName.length()) > 100) {
            throw new ValidationException("customer", "Customer names must be at most 100 characters.");
        }
        if (customerId != null && customerId <= 0) {
            throw new ValidationException("customer", "Please select a customer from the list.");
        }
        BigDecimal amount = parseAmount(amountText);
        validateDate(date);
        // SB08 marks customer and amount as required; items are optional plain text.
        String purchasedItems = items == null ? "" : items.strip();
        if (purchasedItems.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new ValidationException("items", "The items description is too long.");
        }
        return new UtangRequest(customerId, customerName, purchasedItems, amount, date);
    }

    static BigDecimal parseAmount(String amountText) {
        String text = amountText == null ? "" : amountText.strip();
        if (text.length() > 100 || !text.matches("(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})")) {
            throw invalidAmount();
        }
        BigDecimal amount = new BigDecimal(text);
        if (amount.signum() <= 0 || amount.compareTo(MAX_MONEY) > 0) {
            throw invalidAmount();
        }
        return amount.setScale(2);
    }

    private static void validateDate(LocalDate date) {
        if (date == null || date.getYear() < 1000 || date.getYear() > 9999) {
            throw new ValidationException("date", "Please select a valid date.");
        }
    }

    static UtangRequest validate(UtangRequest request) {
        if (request == null) {
            throw new ValidationException("customer", "Enter or select a customer name.");
        }
        return prepareUtang(request.customerId(), request.customerName(), request.items(),
                request.amount() == null ? null : request.amount().toPlainString(), request.date());
    }

    static PaymentRequest preparePayment(int customerId, String amountText, LocalDate date) {
        if (customerId <= 0) {
            throw new ValidationException("customer", "Please select a customer.");
        }
        BigDecimal amount = parseAmount(amountText);
        validateDate(date);
        return new PaymentRequest(customerId, amount, date);
    }

    static PaymentRequest validate(PaymentRequest request) {
        if (request == null) {
            throw new ValidationException("customer", "Please select a customer.");
        }
        return preparePayment(request.customerId(),
                request.amount() == null ? null : request.amount().toPlainString(), request.date());
    }

    static void requireOutstandingBalance(BigDecimal balance) {
        if (balance.signum() <= 0) {
            throw new PaymentBalanceException("This customer has no balance.", balance);
        }
    }

    /** Used for the preview and again with the locked database balance when saving. */
    static BigDecimal subtractPayment(BigDecimal balance, BigDecimal amount) {
        requireOutstandingBalance(balance);
        if (amount.compareTo(balance) > 0) {
            throw new PaymentBalanceException("Payment cannot be more than the balance.", balance);
        }
        return balance.subtract(amount).setScale(2);
    }

    /** Called with the current database balance while its customer row is locked. */
    static BigDecimal addUtang(BigDecimal balance, BigDecimal amount) {
        BigDecimal updated = balance.add(amount);
        if (updated.compareTo(MAX_MONEY) > 0) {
            throw new ValidationException("amount", "This utang would exceed the maximum customer balance.");
        }
        return updated.setScale(2);
    }

    private static ValidationException invalidAmount() {
        return new ValidationException("amount", "Please enter a valid amount.");
    }

    static class ValidationException extends IllegalArgumentException {
        private final String field;

        ValidationException(String field, String message) {
            super(message);
            this.field = field;
        }

        String field() {
            return field;
        }
    }

    /** Supplies the current locked balance when another payment changed it. */
    static final class PaymentBalanceException extends ValidationException {
        private final BigDecimal balance;

        PaymentBalanceException(String message, BigDecimal balance) {
            super("amount", message);
            this.balance = balance;
        }

        BigDecimal balance() {
            return balance;
        }
    }
}
