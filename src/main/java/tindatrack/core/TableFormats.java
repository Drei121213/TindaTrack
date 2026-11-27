package tindatrack.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;

/** Display-only table formatting shared by owner and guest screens. */
public final class TableFormats {
    private TableFormats() { }

    /** Keeps numeric values sortable while displaying pesos and two decimals. */
    public static <T> void money(TableColumn<T, BigDecimal> column) {
        column.setCellFactory(value -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : "₱" + amount.setScale(2).toPlainString());
            }
        });
        column.getStyleClass().add("numeric-column");
    }

    /** Uses the same ISO date format as the product form, including missing dates. */
    public static <T> void date(TableColumn<T, LocalDate> column) {
        column.setCellFactory(value -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty ? null : date == null ? "—" : date.toString());
            }
        });
    }

    /** A tooltip exposes long names or item descriptions when a column is narrow. */
    public static <T> void text(TableColumn<T, String> column) {
        column.setCellFactory(value -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                setTooltip(empty || text == null || text.isBlank() ? null : new Tooltip(text));
            }
        });
    }
}
