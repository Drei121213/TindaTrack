package tindatrack.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Date rules shared by the product form and Expiring Soon view. */
final class ExpirationDates {

    private ExpirationDates() {
    }

    static LocalDate parseOptional(String text) {
        String value = text.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}")) {
            throw new DateTimeParseException("Expected YYYY-MM-DD", value, 0);
        }
        return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    static boolean requiresConfirmation(LocalDate previous, LocalDate date, LocalDate today) {
        return date != null && !date.equals(previous) && date.isBefore(today);
    }

    static String status(LocalDate date, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, date);
        if (days < 0) {
            return "Expired";
        }
        if (days == 0) {
            return "Expires Today";
        }
        return days == 1 ? "1 Day Left" : days + " Days Left";
    }
}
