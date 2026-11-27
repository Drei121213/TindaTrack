package tindatrack.credit;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A payment belongs to one existing customer and has its own amount and date. */
public record PaymentRequest(int customerId, BigDecimal amount, LocalDate date) {
}
