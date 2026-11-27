package tindatrack.credit;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Applies credit rules before asking the DAO to save owner utang or payments. */
public final class CreditService {

    private final CreditDao credits;

    public CreditService(CreditDao credits) {
        this.credits = Objects.requireNonNull(credits);
    }

    public void requireOwnerAccess() {
        credits.requireOwnerAccess();
    }

    public List<Customer> findCustomers() throws IOException, SQLException {
        return credits.findCustomers("");
    }

    public Optional<Customer> findCustomer(int customerId) throws IOException, SQLException {
        return credits.findCustomer(customerId);
    }

    public PaymentRequest preparePayment(int customerId, String amount, LocalDate date) {
        requireOwnerAccess();
        return CreditRules.preparePayment(customerId, amount, date);
    }

    public BigDecimal previewPayment(BigDecimal balance, String amount) {
        requireOwnerAccess();
        return CreditRules.subtractPayment(balance, CreditRules.parseAmount(amount));
    }

    public BigDecimal recordPayment(PaymentRequest request) throws IOException, SQLException {
        requireOwnerAccess();
        return credits.recordPayment(CreditRules.validate(request));
    }

    public UtangRequest prepareUtang(Integer customerId, String name, String items,
                                      String amount, LocalDate date) {
        requireOwnerAccess();
        return CreditRules.prepareUtang(customerId, name, items, amount, date);
    }

    /** Returns the saved customer's ID so the list can select their updated balance. */
    public int recordUtang(UtangRequest request) throws IOException, SQLException {
        requireOwnerAccess();
        return credits.recordUtang(CreditRules.validate(request));
    }
}
