package tindatrack.credit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A read-only history entry from either credit_transactions or payments. */
public record CreditTransaction(int id, String type, String items,
                                BigDecimal amount, LocalDateTime date) {
}
