package fairlease.ui;

import fairlease.exceptions.AgreementStateException;
import fairlease.exceptions.UnfairClaimException;
import fairlease.model.Payment;
import fairlease.service.FairLease;
import fairlease.storage.PhotoVault;

import java.io.PrintStream;

/** The menu loop shared by every role. Refused actions are explained to the user and the data is saved after each action. */
public abstract class RoleMenu {
    protected final FairLeaseApp app;
    protected final FairLease lease;
    protected final ConsoleInput in;
    protected final Views views;
    protected final PhotoVault vault;
    protected final PrintStream out;

    protected RoleMenu(FairLeaseApp app) {
        this.app = app;
        this.lease = app.getLease();
        this.in = app.getInput();
        this.views = app.getViews();
        this.vault = app.getVault();
        this.out = app.getOut();
    }

    public final void run() {
        while (true) {
            int choice = in.menu(title(), "Log out", options());
            if (choice == 0) {
                return;
            }
            try {
                handle(choice);
            } catch (AgreementStateException e) {
                out.println("  Not possible: " + e.getMessage());
            } catch (UnfairClaimException e) {
                out.println("  Claim refused: " + e.getMessage());
            }
            app.save();
        }
    }

    protected abstract String title();

    protected abstract String[] options();

    protected abstract void handle(int choice) throws AgreementStateException, UnfairClaimException;

    protected void printReceipt(Payment payment) {
        out.println();
        views.receipt(payment);
        app.saveDocument(payment.getAgreementId(), "receipt-" + payment.getId() + ".txt", views.capture(v -> v.receipt(payment)));
    }
}
