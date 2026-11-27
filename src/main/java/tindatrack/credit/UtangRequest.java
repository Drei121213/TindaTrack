package tindatrack.credit;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A null customerId means resolve or create the typed customer name when saving. */
public record UtangRequest(Integer customerId, String customerName, String items,
                           BigDecimal amount, LocalDate date) {
}
