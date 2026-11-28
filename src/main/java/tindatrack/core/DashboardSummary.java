package tindatrack.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The three SB02 totals and the cutoff used for the expiration count. */
public record DashboardSummary(long totalProducts, BigDecimal totalCredits,
                               long expiringProducts, LocalDate throughDate) {
}
