package tindatrack.credit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Customer data, including the latest date derived from credit and payment records. */
public record Customer(int id, String name, BigDecimal balance, LocalDateTime lastTransaction) {
}
