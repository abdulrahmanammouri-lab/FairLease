package fairlease.ui;

import fairlease.model.Agreement;
import fairlease.model.CallLogEntry;
import fairlease.model.DeductionClaim;
import fairlease.model.Deposit;
import fairlease.model.Dispute;
import fairlease.model.Inspection;
import fairlease.model.InspectionEntry;
import fairlease.model.Invoice;
import fairlease.model.Item;
import fairlease.model.MaintenanceRequest;
import fairlease.model.Message;
import fairlease.model.Payment;
import fairlease.model.Property;
import fairlease.model.Role;
import fairlease.model.User;
import fairlease.service.FairLease;
import fairlease.storage.PhotoVault;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Everything FairLease prints: statements, reports, chats and case files. */
public class Views {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FairLease lease;
    private final PhotoVault vault;
    private final PrintStream out;

    public Views(FairLease lease, PhotoVault vault, PrintStream out) {
        this.lease = lease;
        this.vault = vault;
        this.out = out;
    }

    /** Renders into a String instead of the console, used to write receipts and case files to disk. */
    public String capture(Consumer<Views> printer) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        printer.accept(new Views(lease, vault, new PrintStream(buffer, true, StandardCharsets.UTF_8)));
        return buffer.toString(StandardCharsets.UTF_8);
    }

    public String money(double amount) {
        return String.format("%,.2f %s", amount, lease.getConfig().getCurrency());
    }

    public static String stamp(LocalDateTime time) {
        return time == null ? "-" : time.format(STAMP);
    }

    public String agreementLine(Agreement a) {
        return a.getId() + "  " + lease.propertyOf(a).getAddress() + "  [" + a.getStatus() + "]  "
                + lease.nameOf(a.getLandlordId()) + " -> " + lease.nameOf(a.getTenantId());
    }

    public void agreement(Agreement a) {
        Property p = lease.propertyOf(a);
        out.println("Agreement " + a.getId() + " - " + a.getStatus());
        out.println("  Property : " + p);
        out.println("  Landlord : " + lease.nameOf(a.getLandlordId()));
        out.println("  Tenant   : " + lease.nameOf(a.getTenantId()));
        out.println("  Period   : " + a.getStartDate() + " to " + a.getEndDate());
        out.println("  Rent     : " + money(a.getMonthlyRent()) + ", due on day " + a.getDueDay() + " of each month");
        out.println("  Deposit  : " + money(a.getDepositAmount()) + " (" + lease.depositOf(a).getStatus() + ")");
        out.println("  Included in rent: " + (a.getIncludedBills().isEmpty() ? "nothing" : a.getIncludedBills()));
        out.println("  House rules: " + (a.getHouseRules() == null ? "-" : a.getHouseRules()));
        if (a.getSignedAt() != null) {
            out.println("  Signed by the tenant on " + stamp(a.getSignedAt()));
        }
    }

    public void property(Property p) {
        out.println(p.getId() + "  " + p + "  [" + p.getStatus() + "]");
        for (String room : p.getRooms()) {
            StringBuilder line = new StringBuilder("    " + room + ": ");
            List<Item> items = p.getItemsIn(room);
            for (int i = 0; i < items.size(); i++) {
                line.append(i == 0 ? "" : ", ").append(items.get(i).getName());
            }
            out.println(line);
        }
    }

    public void statement(Agreement a, LocalDate today) {
        List<Invoice> open = lease.openInvoices(a);
        out.println("Monthly statement - " + lease.propertyOf(a).getAddress());
        if (open.isEmpty()) {
            out.println("  Everything is paid. Thank you!");
            return;
        }
        for (Invoice i : open) {
            String flag = i.isContested() ? "ON HOLD: " + i.getContestReason()
                    : i.isOverdue(a.dueDate(i.getPeriod()), today) ? "OVERDUE since " + a.dueDate(i.getPeriod()) : "due " + a.dueDate(i.getPeriod());
            out.printf("  %-6s %-12s %-8s %16s   %s%n", i.getId(), i.getType(), i.getPeriod(), money(i.getAmount()), flag);
            if (i.hasPhoto()) {
                out.println("         bill " + vault.describe(i));
            }
        }
        out.println("  Total to pay now: " + money(lease.unpaidTotal(a)));
    }

    public void history(Agreement a) {
        out.println("Invoices of " + a.getId() + ":");
        for (Invoice i : lease.invoicesOf(a)) {
            out.printf("  %-6s %-12s %-8s %16s  %-8s %s%n", i.getId(), i.getType(), i.getPeriod(), money(i.getAmount()), i.getStatus(),
                    i.getPaidOn() == null ? "" : "paid " + i.getPaidOn());
        }
        out.println("Payments:");
        List<Payment> payments = lease.paymentsOf(a);
        if (payments.isEmpty()) {
            out.println("  none yet");
        }
        for (Payment p : payments) {
            out.printf("  %-6s %s %16s  %-13s for %s%n", p.getId(), p.getDate(), money(p.getAmount()), p.getMethod(),
                    String.join(", ", p.getPaidIds()));
        }
    }

    public void receipt(Payment p) {
        Agreement a = lease.agreement(p.getAgreementId()).orElseThrow();
        out.println("FAIRLEASE PAYMENT RECEIPT " + p.getId());
        out.println("Date      : " + p.getDate());
        out.println("Property  : " + lease.propertyOf(a).getAddress());
        out.println("Paid by   : " + lease.nameOf(a.getTenantId()));
        out.println("Paid to   : " + lease.nameOf(a.getLandlordId()));
        out.println("Method    : " + p.getMethod());
        out.println("Covers    : " + String.join(", ", p.getPaidIds()));
        out.println("Amount    : " + money(p.getAmount()));
    }

    public void inspection(Inspection inspection) {
        Agreement a = lease.agreement(inspection.getAgreementId()).orElseThrow();
        Property p = lease.propertyOf(a);
        out.println(inspection.getType() + " report " + inspection.getId() + " - " + p.getAddress());
        out.println("  Submitted: " + stamp(inspection.getSubmittedAt()) + "   Final: " + (inspection.isFinal() ? "yes" : "no"));
        if (inspection.getLandlordComment() != null) {
            out.println("  Landlord's comment: " + inspection.getLandlordComment());
        }
        for (Item item : p.getItems()) {
            Optional<InspectionEntry> entry = inspection.entryFor(item.getId());
            out.println("  " + item + ": " + entry.map(this::entryText).orElse("not inspected yet"));
        }
    }

    /** Move-in and move-out side by side, so new damage is easy to see. */
    public void comparison(Agreement a) {
        Optional<Inspection> in = lease.inspectionOf(a, Inspection.Type.MOVE_IN);
        Optional<Inspection> outReport = lease.inspectionOf(a, Inspection.Type.MOVE_OUT);
        if (in.isEmpty()) {
            out.println("  No move-in report yet.");
            return;
        }
        out.println("Item                               MOVE-IN        MOVE-OUT");
        for (Item item : lease.propertyOf(a).getItems()) {
            Optional<InspectionEntry> before = in.get().entryFor(item.getId());
            Optional<InspectionEntry> after = outReport.flatMap(r -> r.entryFor(item.getId()));
            boolean worse = before.isPresent() && after.isPresent() && after.get().getCondition().isWorseThan(before.get().getCondition());
            out.printf("  %-32s %-14s %-14s%s%n", item, before.map(this::shortEntry).orElse("-"),
                    after.map(this::shortEntry).orElse("-"), worse ? "  <-- NEW DAMAGE" : "");
        }
    }

    private String shortEntry(InspectionEntry e) {
        return e.getMeterReading() != null ? String.valueOf(e.getMeterReading()) : e.getCondition().toString();
    }

    private String entryText(InspectionEntry e) {
        StringBuilder text = new StringBuilder(e.getMeterReading() != null ? "reading " + e.getMeterReading() : e.getCondition().toString());
        if (e.getNote() != null) {
            text.append(" - ").append(e.getNote());
        }
        return text.append(" [").append(vault.describe(e)).append("]").toString();
    }

    public void deposit(Agreement a) {
        Deposit d = lease.depositOf(a);
        out.println("Security deposit of " + a.getId() + ": " + money(d.getAmount()) + " - " + d.getStatus());
        if (d.getPaidOn() != null) {
            out.println("  Paid on " + d.getPaidOn() + ", held by FairLease (not by the landlord).");
        }
        if (d.getStatus() == Deposit.Status.RETURNED) {
            out.println("  Returned " + money(d.getReturnedAmount()) + " on " + d.getReturnedOn() + ".");
        }
        for (DeductionClaim c : d.getClaims()) {
            out.println("  " + claimLine(a, c));
        }
    }

    public String claimLine(Agreement a, DeductionClaim c) {
        String item = lease.item(a, c.getItemId()).map(Item::toString).orElse(c.getItemId());
        return c.getId() + "  " + item + "  " + money(c.getAmount()) + "  [" + c.getStatus() + "]  " + c.getReason()
                + "  (" + vault.describe(c) + ")";
    }

    public void chat(Agreement a) {
        List<Message> messages = lease.chatOf(a);
        out.println("Chat of agreement " + a.getId() + " (messages cannot be deleted):");
        if (messages.isEmpty()) {
            out.println("  no messages yet");
        }
        for (Message m : messages) {
            out.println("  [" + stamp(m.getSentAt()) + "] " + lease.nameOf(m.getSenderId()) + ": " + m.getText());
        }
    }

    public void repairs(Agreement a) {
        List<MaintenanceRequest> repairs = lease.repairsOf(a);
        if (repairs.isEmpty()) {
            out.println("  No repair requests.");
        }
        for (MaintenanceRequest r : repairs) {
            out.println("  " + repairLine(r));
        }
    }

    public String repairLine(MaintenanceRequest r) {
        return r.getId() + "  " + r.getUrgency() + "  " + r.getStatus() + "  " + r.getDescription()
                + "  (reported " + stamp(r.getCreatedAt()) + ", " + vault.describe(r) + ")";
    }

    public String disputeLine(Dispute d) {
        return d.getId() + "  " + d.getTopic() + "  [" + d.getStatus() + "]  agreement " + d.getAgreementId() + "  " + d.getDescription();
    }

    public void dispute(Dispute d) {
        out.println("Case " + d.getId() + " - " + d.getTopic() + " - " + d.getStatus());
        out.println("  Opened by " + lease.nameOf(d.getOpenedBy()) + " on " + stamp(d.getOpenedAt()));
        out.println("  " + d.getDescription());
        if (d.getAgentId() != null) {
            out.println("  Agent: " + lease.nameOf(d.getAgentId()));
        }
        if (d.getProposal() != null) {
            out.println("  Proposal: " + d.getProposal()
                    + (d.getProposedAmount() == null ? "" : " (agreed amount " + money(d.getProposedAmount()) + ")"));
            out.println("  Tenant: " + answer(d.getTenantAccepts()) + "   Landlord: " + answer(d.getLandlordAccepts()));
        }
        for (CallLogEntry e : lease.getCallCenter().logFor(d)) {
            out.println("  call " + stamp(e.getAt()) + " (" + lease.nameOf(e.getAgentId()) + "): " + e.getSummary());
        }
    }

    private static String answer(Boolean accepted) {
        return accepted == null ? "no answer yet" : accepted ? "accepts" : "rejects";
    }

    /** Everything an agent (or a legal authority) needs to understand a case. */
    public void caseFile(Dispute d) {
        Agreement a = lease.agreement(d.getAgreementId()).orElseThrow();
        out.println("================ FAIRLEASE CASE FILE " + d.getId() + " ================");
        out.println("Generated " + stamp(LocalDateTime.now()));
        out.println();
        dispute(d);
        out.println();
        agreement(a);
        out.println();
        comparison(a);
        for (Inspection.Type type : Inspection.Type.values()) {
            lease.inspectionOf(a, type).ifPresent(i -> {
                out.println();
                inspection(i);
            });
        }
        out.println();
        deposit(a);
        out.println();
        history(a);
        out.println();
        out.println("Repairs:");
        repairs(a);
        out.println();
        chat(a);
    }

    public void notifications(User user, LocalDate today) {
        int count = 0;
        for (Agreement a : lease.agreementsOf(user)) {
            if (user.getRole() == Role.TENANT) {
                if (a.getStatus() == Agreement.Status.PENDING) {
                    out.println("  * Agreement " + a.getId() + " is waiting for your signature.");
                    count++;
                }
                long overdue = lease.openInvoices(a).stream().filter(i -> i.isOverdue(a.dueDate(i.getPeriod()), today)).count();
                if (overdue > 0) {
                    out.println("  * " + overdue + " overdue invoice(s) on " + a.getId() + ". Total unpaid: " + money(lease.unpaidTotal(a)));
                    count++;
                }
                if (a.getStatus() == Agreement.Status.ACTIVE && lease.depositOf(a).isPayable()) {
                    out.println("  * The deposit for " + a.getId() + " is not paid yet.");
                    count++;
                }
                for (DeductionClaim c : lease.depositOf(a).getClaims()) {
                    if (c.getStatus() == DeductionClaim.Status.PENDING) {
                        out.println("  * The landlord claims " + money(c.getAmount()) + " from your deposit: " + c.getReason());
                        count++;
                    }
                }
            } else if (user.getRole() == Role.LANDLORD) {
                if (lease.inspectionOf(a, Inspection.Type.MOVE_IN).filter(i -> i.isSubmitted() && !i.isFinal()).isPresent()
                        || lease.inspectionOf(a, Inspection.Type.MOVE_OUT).filter(i -> i.isSubmitted() && !i.isFinal()).isPresent()) {
                    out.println("  * An inspection report of " + a.getId() + " is waiting for your confirmation.");
                    count++;
                }
                long openRepairs = lease.repairsOf(a).stream().filter(r -> r.getStatus() == MaintenanceRequest.Status.OPEN).count();
                if (openRepairs > 0) {
                    out.println("  * " + openRepairs + " new repair request(s) on " + a.getId() + ".");
                    count++;
                }
            }
        }
        if (user.getRole() != Role.AGENT) {
            for (Dispute d : lease.disputesOf(user)) {
                if (d.isWaitingForAnswerFrom(user.getRole())) {
                    out.println("  * The call center proposed a resolution for case " + d.getId() + ".");
                    count++;
                }
            }
        } else {
            int waiting = lease.getCallCenter().waitingQueue().size();
            if (waiting > 0) {
                out.println("  * " + waiting + " case(s) waiting in the call center queue.");
                count++;
            }
        }
        if (count == 0) {
            out.println("  No new notifications.");
        }
    }

    public void report() {
        out.println("================ FAIRLEASE REPORT " + LocalDate.now() + " ================");
        out.println("Users: " + lease.getUsers().size() + "   Properties: " + lease.getProperties().size()
                + "   Agreements: " + lease.getAgreements().size());
        for (Property p : lease.getProperties()) {
            out.println();
            out.println(p.getId() + "  " + p + "  [" + p.getStatus() + "]  owner " + lease.nameOf(p.getLandlordId()));
            for (Agreement a : lease.getAgreements()) {
                if (!a.getPropertyId().equals(p.getId())) {
                    continue;
                }
                Deposit d = lease.depositOf(a);
                long openCases = lease.getCallCenter().disputesFor(a.getId()).stream().filter(Dispute::isOpen).count();
                out.println("   " + a.getId() + "  tenant " + lease.nameOf(a.getTenantId()) + "  " + a.getStartDate() + " to " + a.getEndDate()
                        + "  [" + a.getStatus() + "]");
                out.println("        unpaid " + money(lease.unpaidTotal(a)) + "   deposit " + money(d.getAmount()) + " " + d.getStatus()
                        + "   open cases " + openCases);
            }
        }
        out.println();
        long open = lease.getCallCenter().getDisputes().stream().filter(Dispute::isOpen).count();
        out.println("Call center: " + open + " open case(s), " + lease.getCallCenter().getDisputes().size() + " in total, "
                + lease.getCallCenter().getCallLog().size() + " logged call(s).");
    }
}
