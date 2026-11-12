package tindatrack.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A row from the products table. The expiration date can be null. */
public record Product(int id, String name, BigDecimal price, int stockQuantity,
                      LocalDate expirationDate) {
}
