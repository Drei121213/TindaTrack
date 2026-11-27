package tindatrack.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Date rules shared by the product form and Expiring Soon view. */
final class ExpirationDates {

    private ExpirationDates() {
    }

    /**
     * Parses a stripped YYYY-MM-DD value with a year from 1000 to 9999.
     * @return null for blank input, or a valid calendar date
     * @throws DateTimeParseException for an incorrect format or impossible date
     */
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

    /**
     * Requests confirmation only for a non-null expiration date that changed and is
     * before today. Keeping an existing past date does not trigger another warning.
     * @return true when the proposed date needs the past-date confirmation
     */
    static boolean requiresConfirmation(LocalDate previous, LocalDate date, LocalDate today) {
        return date != null && !date.equals(previous) && date.isBefore(today);
    }

    /**
     * Counts calendar days from today to the expiration date: negative means expired,
     * zero means expires today, and positive values give the remaining day count.
     * @param date non-null expiration date to label
     * @return the display label for the calculated day count
     */
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
