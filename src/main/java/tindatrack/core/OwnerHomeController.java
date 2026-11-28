package tindatrack.core;

import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.time.LocalDate;
import tindatrack.core.SessionState.AccessDeniedException;

/** Displays the owner greeting and read-only SB02 summary cards. */
public class OwnerHomeController {
    @FXML private Label ownerGreeting;
    @FXML private Label totalProducts;
    @FXML private Label totalCredits;
    @FXML private Label expiringProducts;
    @FXML private Label dashboardRange;
    @FXML private Label dashboardStatus;
    @FXML private Button dashboardRefreshButton;

    private DashboardDao dashboard;
    private boolean active;
    private Task<DashboardSummary> currentLoad;

    public void configure(String ownerName, DashboardDao dashboard) {
        ownerGreeting.setText("Welcome, " + ownerName + "!");
        this.dashboard = dashboard;
    }

    public void activate() {
        dashboard.requireOwnerAccess();
        active = true;
        loadSummary();
    }

    public void deactivate() {
        active = false;
        if (currentLoad != null) {
            currentLoad.cancel();
            currentLoad = null;
        }
        clearTotals();
    }

    @FXML
    private void loadSummary() {
        if (!active || currentLoad != null) return;
        clearTotals();
        dashboardRefreshButton.setDisable(true);
        showStatus("Loading store summary...", false);
        Task<DashboardSummary> task = new Task<>() {
            @Override protected DashboardSummary call() throws Exception {
                return dashboard.readSummary(LocalDate.now());
            }
        };
        currentLoad = task;
        task.setOnSucceeded(event -> {
            if (!active || currentLoad != task) return;
            currentLoad = null;
            try {
                dashboard.requireOwnerAccess();
                DashboardSummary summary = task.getValue();
                totalProducts.setText(Long.toString(summary.totalProducts()));
                totalCredits.setText("₱" + summary.totalCredits().setScale(2).toPlainString());
                expiringProducts.setText(Long.toString(summary.expiringProducts()));
                dashboardRange.setText("Expiration count includes expired items and dates through " + summary.throughDate() + ".");
                showStatus("Store summary updated.", false);
                dashboardRefreshButton.setDisable(false);
            } catch (AccessDeniedException denied) {
                clearTotals();
                showStatus(denied.getMessage(), true);
            }
        });
        task.setOnFailed(event -> {
            if (!active || currentLoad != task) return;
            currentLoad = null;
            clearTotals();
            boolean ended = task.getException() instanceof AccessDeniedException;
            showStatus(ended ? task.getException().getMessage()
                    : "Cannot load the store summary. Please try again.", true);
            dashboardRefreshButton.setDisable(ended);
        });
        Thread worker = new Thread(task, "tindatrack-dashboard-summary");
        worker.setDaemon(true);
        worker.start();
    }

    private void clearTotals() {
        totalProducts.setText("—");
        totalCredits.setText("—");
        expiringProducts.setText("—");
        dashboardRange.setText("Expiration count includes expired items and products due within 7 days.");
    }

    private void showStatus(String message, boolean error) {
        dashboardStatus.setText(message);
        dashboardStatus.pseudoClassStateChanged(PseudoClass.getPseudoClass("error"), error);
    }
}
