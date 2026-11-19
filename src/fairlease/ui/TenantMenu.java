package fairlease.ui;

import fairlease.config.AppConfig;
import fairlease.exceptions.AgreementStateException;
import fairlease.model.Agreement;
import fairlease.model.Condition;
import fairlease.model.DeductionClaim;
import fairlease.model.Deposit;
import fairlease.model.Dispute;
import fairlease.model.Inspection;
import fairlease.model.InspectionEntry;
import fairlease.model.Invoice;
import fairlease.model.Item;
import fairlease.model.Landlord;
import fairlease.model.MaintenanceRequest;
import fairlease.model.Payment;
import fairlease.model.Photo;
import fairlease.model.Property;
import fairlease.model.Tenant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TenantMenu extends PartyMenu {
    private final Tenant tenant;

    public TenantMenu(FairLeaseApp app, Tenant tenant) {
        super(app, tenant);
        this.tenant = tenant;
    }

    @Override
    protected String title() {
        return "Tenant - " + tenant.getName();
    }

    @Override
    protected String[] options() {
        return new String[]{
                "My agreements (view, sign or reject)",
                "Move-in inspection (photos of the house)",
                "Monthly statement and payment",
                "Contest a bill",
                "Security deposit",
                "Report a problem / repair",
                "Move-out inspection",
                "Chat with the landlord",
                "Call center"
        };
    }

    @Override
    protected void handle(int choice) throws AgreementStateException {
        switch (choice) {
            case 1 -> agreements();
            case 2 -> inspection(Inspection.Type.MOVE_IN);
            case 3 -> statement();
            case 4 -> contestBill();
            case 5 -> deposit();
            case 6 -> repair();
            case 7 -> inspection(Inspection.Type.MOVE_OUT);
            case 8 -> chat();
            case 9 -> callCenter();
            default -> { }
        }
    }

    private void agreements() throws AgreementStateException {
        Agreement agreement = in.pick("Your agreements", lease.agreementsOf(tenant), views::agreementLine);
        if (agreement == null) {
            return;
        }
        views.agreement(agreement);
        if (agreement.getStatus() != Agreement.Status.PENDING) {
            return;
        }
        int choice = in.menu("Agreement " + agreement.getId(), "Decide later", "Sign electronically", "Reject");
        if (choice == 1 && in.yesNo("By signing you accept every term above. Sign now?")) {
            lease.signAgreement(tenant, agreement);
            out.println("  Signed. Next steps: pay the deposit and do the move-in inspection before you receive the keys.");
        } else if (choice == 2 && in.yesNo("Reject this agreement?")) {
            lease.rejectAgreement(tenant, agreement);
            out.println("  Rejected. The landlord can prepare a new one.");
        }
    }

    private void inspection(Inspection.Type type) throws AgreementStateException {
        Agreement agreement = chooseAgreement(type + " inspection of which rental", a -> a.getStatus() == Agreement.Status.ACTIVE);
        if (agreement == null) {
            return;
        }
        Inspection inspection = lease.startInspection(tenant, agreement, type);
        if (inspection.getLandlordComment() != null) {
            out.println("  The landlord sent the report back: \"" + inspection.getLandlordComment() + "\"");
        }
        Optional<Inspection> moveIn = type == Inspection.Type.MOVE_OUT
                ? lease.inspectionOf(agreement, Inspection.Type.MOVE_IN) : Optional.empty();
        Property property = lease.propertyOf(agreement);
        out.println("Walk through the house room by room. For every item choose its condition, describe any damage and take a photo.");
        if (app.getConfig().getPhotoPolicy() == AppConfig.PhotoPolicy.DAMAGED_ONLY) {
            out.println("(Photos are required for worn or damaged items and for meters.)");
        }

        List<String> rooms = new ArrayList<>(property.getRooms());
        for (int r = 0; r < rooms.size(); r++) {
            String room = rooms.get(r);
            List<Item> items = property.getItemsIn(room);
            boolean done = items.stream().allMatch(i -> inspection.entryFor(i.getId()).isPresent());
            out.println();
            out.println("--- " + room + (done ? " (already recorded)" : "") + " ---");
            boolean redo = done && in.yesNo("Record this room again?");
            for (Item item : items) {
                if (inspection.entryFor(item.getId()).isPresent() && !redo) {
                    continue;
                }
                moveIn.flatMap(report -> report.entryFor(item.getId())).ifPresent(before ->
                        out.println("  " + item.getName() + " at move-in: " + before.getCondition()
                                + (before.getNote() == null ? "" : " - " + before.getNote())
                                + (before.getMeterReading() == null ? "" : ", reading " + before.getMeterReading())));
                inspection.record(recordItem(item));
                app.save();
            }
            if (r < rooms.size() - 1 && !in.yesNo("Continue with the next room?")) {
                out.println("  Progress saved. You can continue later from the same menu.");
                return;
            }
        }

        out.println();
        views.inspection(inspection);
        if (in.yesNo("Submit this report to the landlord? It cannot be changed afterwards")) {
            lease.submitInspection(inspection);
            out.println("  Submitted. The landlord has " + app.getConfig().getInspectionResponseHours()
                    + " hours to confirm it; after that it is accepted automatically.");
        }
    }

    private InspectionEntry recordItem(Item item) {
        if (item.isMeter()) {
            double reading = in.ask("  " + item.getName() + " reading: ", raw -> InputRules.decimal(raw, "Reading", 0, 99_999_999, 3));
            Photo photo = in.photo("  Path to a photo of the " + item.getName().toLowerCase(), vault, true);
            return new InspectionEntry(item.getId(), Condition.GOOD, null, reading, photo);
        }
        Condition condition = in.choice("  Condition of " + item.getName() + ":", Condition.values());
        String note = condition.showsDamage()
                ? in.text("  Describe the damage (scratch, stain, crack, broken part...): ", "Description", 3, 200)
                : in.askOptional("  Note (Enter for none): ", raw -> InputRules.text(raw, "Note", 1, 200));
        boolean photoRequired = condition.showsDamage() || app.getConfig().getPhotoPolicy() == AppConfig.PhotoPolicy.ALL_ITEMS;
        Photo photo = in.photo("  Path to a photo of the " + item.getName().toLowerCase(), vault, photoRequired);
        return new InspectionEntry(item.getId(), condition, note, null, photo);
    }

    private void statement() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Statement of which rental", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        views.statement(agreement, today);
        double total = lease.unpaidTotal(agreement);
        if (total == 0 || !in.yesNo("Pay " + views.money(total) + " now, in one payment?")) {
            return;
        }
        Payment.Method method = askPaymentMethod(agreement, total);
        if (method != null) {
            printReceipt(lease.payStatement(tenant, agreement, method, today));
        }
    }

    /** Card details are validated but never stored: the payment provider handles them. */
    private Payment.Method askPaymentMethod(Agreement agreement, double amount) {
        Payment.Method method = in.choice("Payment method:", new Payment.Method[]{Payment.Method.CARD, Payment.Method.BANK_TRANSFER});
        if (method == Payment.Method.CARD) {
            String card = in.ask("Card number: ", InputRules::cardNumber);
            in.ask("Expiry (MM/YY): ", raw -> InputRules.cardExpiry(raw, LocalDate.now()));
            in.ask("CVV: ", InputRules::cvv);
            out.println("  Card ending in " + card.substring(card.length() - 4) + " accepted by the payment provider.");
            return method;
        }
        Landlord landlord = (Landlord) lease.user(agreement.getLandlordId()).orElseThrow();
        out.println("  Transfer " + views.money(amount) + " to " + landlord.getName() + ", IBAN " + landlord.getIban()
                + ", reference FAIRLEASE-" + agreement.getId());
        return in.yesNo("Have you sent the transfer?") ? method : null;
    }

    private void contestBill() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Bill of which rental", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        List<Invoice> payable = new ArrayList<>();
        for (Invoice invoice : lease.openInvoices(agreement)) {
            if (invoice.isPayable()) {
                payable.add(invoice);
            }
        }
        Invoice invoice = in.pick("Which bill do you contest", payable,
                i -> i.getId() + "  " + i.describe() + "  " + views.money(i.getAmount()) + "  (" + vault.describe(i) + ")");
        if (invoice == null) {
            return;
        }
        String reason = in.text("Why do you contest it? ", "Reason", 5, 300);
        Dispute dispute = lease.contestBill(tenant, invoice, reason);
        out.println("  The bill is on hold (you do not have to pay it now) and case " + dispute.getId() + " was sent to the call center.");
    }

    private void deposit() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Deposit of which rental", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        views.deposit(agreement);
        Deposit deposit = lease.depositOf(agreement);
        if (deposit.isPayable() && in.yesNo("Pay the deposit of " + views.money(deposit.getAmount())
                + " now? FairLease keeps it in a protected account until you move out")) {
            Payment.Method method = askPaymentMethod(agreement, deposit.getAmount());
            if (method != null) {
                printReceipt(lease.payDeposit(tenant, agreement, method, LocalDate.now()));
            }
            return;
        }
        for (DeductionClaim claim : deposit.getClaims()) {
            if (claim.getStatus() != DeductionClaim.Status.PENDING) {
                continue;
            }
            out.println();
            out.println("Claim: " + views.claimLine(agreement, claim));
            int choice = in.menu("Your answer", "Decide later", "Accept the deduction", "Dispute it (call center)");
            if (choice == 1) {
                lease.acceptClaim(tenant, claim);
                out.println("  Accepted.");
            } else if (choice == 2) {
                String reason = in.text("Why do you disagree? ", "Reason", 5, 300);
                Dispute dispute = lease.disputeClaim(tenant, claim, reason);
                out.println("  Case " + dispute.getId() + " opened. An agent will compare the move-in and move-out photos.");
            }
        }
    }

    private void repair() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Repair in which rental", a -> a.getStatus() == Agreement.Status.ACTIVE);
        if (agreement == null) {
            return;
        }
        views.repairs(agreement);
        if (!in.yesNo("Report a new problem?")) {
            return;
        }
        String description = in.text("What is the problem? ", "Description", 5, 300);
        MaintenanceRequest.Urgency urgency = in.choice("How urgent is it?", MaintenanceRequest.Urgency.values());
        Photo photo = in.photo("Path to a photo of the problem", vault, false);
        MaintenanceRequest request = lease.reportRepair(tenant, agreement, description, urgency, photo);
        out.println("  Request " + request.getId() + " sent to the landlord.");
        if (urgency == MaintenanceRequest.Urgency.URGENT) {
            out.println("  If it is not handled within " + app.getConfig().getRepairEscalationHours()
                    + " hours, the call center is alerted automatically.");
        }
    }
}
