package fairlease.ui;

import fairlease.exceptions.AgreementStateException;
import fairlease.model.Agreement;
import fairlease.model.Dispute;
import fairlease.model.User;
import fairlease.service.CallCenter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Actions available to both sides of a rental: the chat and the call center. */
public abstract class PartyMenu extends RoleMenu {
    private final User user;

    protected PartyMenu(FairLeaseApp app, User user) {
        super(app);
        this.user = user;
    }

    protected Agreement chooseAgreement(String title, Predicate<Agreement> filter) {
        List<Agreement> matching = new ArrayList<>();
        for (Agreement a : lease.agreementsOf(user)) {
            if (filter.test(a)) {
                matching.add(a);
            }
        }
        return in.pick(title, matching, views::agreementLine);
    }

    protected static boolean isRunningOrEnded(Agreement a) {
        return a.getStatus() == Agreement.Status.ACTIVE || a.getStatus() == Agreement.Status.ENDED;
    }

    protected void chat() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Chat about which agreement", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        views.chat(agreement);
        String text = in.askOptional("Your message (Enter to go back): ", raw -> InputRules.text(raw, "Message", 1, 500));
        if (text != null) {
            lease.sendMessage(user, agreement, text);
            out.println("  Sent. It stays in the chat as a written record.");
        }
    }

    protected void callCenter() throws AgreementStateException {
        CallCenter center = lease.getCallCenter();
        out.println();
        out.println("FairLease call center - phone +40 800 324 753, working hours " + center.workingHours()
                + (center.isOpenAt(LocalTime.now()) ? " (open now)" : " (closed now: new cases are answered on the next working day)"));
        int choice = in.menu("Call center", "Back", "My cases", "Open a new case", "Answer a proposed resolution");
        switch (choice) {
            case 1 -> showCases();
            case 2 -> openCase();
            case 3 -> answerProposal();
            default -> { }
        }
    }

    private void showCases() {
        List<Dispute> cases = lease.disputesOf(user);
        if (cases.isEmpty()) {
            out.println("  You have no cases.");
        }
        for (Dispute d : cases) {
            out.println();
            views.dispute(d);
        }
    }

    private void openCase() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Which rental is the case about", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        out.println("To contest a bill or a deposit deduction, use those menus: the amount is then corrected automatically when the case is resolved.");
        Dispute.Topic topic = in.choice("Topic:", new Dispute.Topic[]{Dispute.Topic.DAMAGE, Dispute.Topic.REPAIR, Dispute.Topic.BEHAVIOUR});
        String description = in.text("Describe the problem: ", "Description", 10, 500);
        Dispute dispute = lease.openDispute(user, agreement, topic, description, null);
        out.println("  Case " + dispute.getId() + " was sent to the call center. An agent will contact both sides.");
    }

    private void answerProposal() throws AgreementStateException {
        List<Dispute> waiting = new ArrayList<>();
        for (Dispute d : lease.disputesOf(user)) {
            if (d.isWaitingForAnswerFrom(user.getRole())) {
                waiting.add(d);
            }
        }
        Dispute dispute = in.pick("Proposals waiting for your answer", waiting, views::disputeLine);
        if (dispute == null) {
            return;
        }
        views.dispute(dispute);
        boolean accept = in.yesNo("Do you accept this resolution?");
        if (lease.answerProposal(user, dispute, accept)) {
            out.println("  Both sides accepted: the case is resolved and the agreed amount was applied.");
        } else if (accept) {
            out.println("  Accepted. Waiting for the other side's answer.");
        } else {
            out.println("  Rejected. The agent will contact you again.");
        }
    }
}
