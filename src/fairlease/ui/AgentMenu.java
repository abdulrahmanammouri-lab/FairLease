package fairlease.ui;

import fairlease.exceptions.AgreementStateException;
import fairlease.model.Agreement;
import fairlease.model.CallCenterAgent;
import fairlease.model.Disputable;
import fairlease.model.Dispute;

import java.time.LocalDateTime;
import java.util.Optional;

public class AgentMenu extends RoleMenu {
    private final CallCenterAgent agent;

    public AgentMenu(FairLeaseApp app, CallCenterAgent agent) {
        super(app);
        this.agent = agent;
    }

    @Override
    protected String title() {
        return "Call center - " + agent.getName();
    }

    @Override
    protected String[] options() {
        return new String[]{"Waiting cases (take one)", "My cases"};
    }

    @Override
    protected void handle(int choice) throws AgreementStateException {
        if (choice == 1) {
            takeCase();
        } else if (choice == 2) {
            myCases();
        }
    }

    private void takeCase() throws AgreementStateException {
        Dispute dispute = in.pick("Waiting cases, oldest first", lease.getCallCenter().waitingQueue(), views::disputeLine);
        if (dispute == null) {
            return;
        }
        views.dispute(dispute);
        if (in.yesNo("Take this case?")) {
            lease.takeCase(agent, dispute);
            out.println("  Case " + dispute.getId() + " is yours. Find it under \"My cases\".");
        }
    }

    private void myCases() throws AgreementStateException {
        Dispute dispute = in.pick("Your cases", lease.getCallCenter().casesOf(agent), views::disputeLine);
        if (dispute == null) {
            return;
        }
        Agreement agreement = lease.agreement(dispute.getAgreementId()).orElseThrow();
        while (true) {
            out.println();
            views.dispute(dispute);
            printContacts(agreement);
            int choice = in.menu("Case " + dispute.getId(), "Back",
                    "View the full case file (agreement, photos, payments, chat)",
                    "Log a call",
                    "Propose a resolution",
                    "Export the case file to a document",
                    "Close the case without agreement");
            switch (choice) {
                case 0 -> {
                    return;
                }
                case 1 -> views.caseFile(dispute);
                case 2 -> {
                    String summary = in.text("Call summary: ", "Summary", 5, 500);
                    lease.getCallCenter().logCall(dispute, agent, summary, LocalDateTime.now());
                    out.println("  Call logged.");
                }
                case 3 -> propose(dispute);
                case 4 -> app.saveDocument(agreement.getId(), "case-" + dispute.getId() + ".txt", views.capture(v -> v.caseFile(dispute)));
                case 5 -> {
                    if (in.yesNo("Close without agreement? The exported case file can then be used before a legal authority")) {
                        lease.closeWithoutAgreement(agent, dispute);
                        out.println("  Case closed.");
                    }
                }
                default -> { }
            }
            app.save();
        }
    }

    private void printContacts(Agreement agreement) {
        for (String id : new String[]{agreement.getTenantId(), agreement.getLandlordId()}) {
            lease.user(id).ifPresent(u -> out.println("  Contact: " + u + ", phone " + u.getPhone()));
        }
    }

    private void propose(Dispute dispute) throws AgreementStateException {
        String proposal = in.text("Proposed resolution: ", "Proposal", 5, 500);
        Double amount = null;
        Optional<Disputable> subject = dispute.getRelatedId() == null ? Optional.empty() : lease.findDisputable(dispute.getRelatedId());
        if (subject.isPresent()) {
            double current = subject.get().getAmount();
            out.println("  " + subject.get().describe() + " currently asks for " + views.money(current) + ".");
            amount = in.money("Amount agreed after mediation (0 to " + current + "): ", "Amount", 0, current);
        }
        lease.proposeResolution(agent, dispute, proposal, amount);
        out.println("  Proposal sent. It is applied automatically when both sides accept it.");
    }
}
