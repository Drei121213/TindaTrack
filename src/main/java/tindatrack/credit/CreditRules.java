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
        String text = amountText == null ? "" : amountText.strip();
        if (text.length() > 100 || !text.matches("(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})")) {
            throw invalidAmount();
        }
        BigDecimal amount = new BigDecimal(text);
        if (amount.signum() <= 0 || amount.compareTo(MAX_MONEY) > 0) {
            throw invalidAmount();
        }
        if (date == null || date.getYear() < 1000 || date.getYear() > 9999) {
            throw new ValidationException("date", "Please select a valid date.");
        }
        // SB08 marks customer and amount as required; items are optional plain text.
        String purchasedItems = items == null ? "" : items.strip();
        if (purchasedItems.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new ValidationException("items", "The items description is too long.");
        }
        return new UtangRequest(customerId, customerName, purchasedItems, amount.setScale(2), date);
    }

    static UtangRequest validate(UtangRequest request) {
        if (request == null) {
            throw new ValidationException("customer", "Enter or select a customer name.");
        }
        return prepareUtang(request.customerId(), request.customerName(), request.items(),
                request.amount() == null ? null : request.amount().toPlainString(), request.date());
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

    static final class ValidationException extends IllegalArgumentException {
        private final String field;

        ValidationException(String field, String message) {
            super(message);
            this.field = field;
        }

        String field() {
            return field;
        }
    }
}
