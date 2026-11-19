package fairlease.service;

import fairlease.exceptions.AgreementStateException;
import fairlease.model.CallCenterAgent;
import fairlease.model.CallLogEntry;
import fairlease.model.Dispute;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The neutral mediator between tenants and landlords: a queue of disputes and a log of every call. */
public class CallCenter {
    private final LocalTime opensAt;
    private final LocalTime closesAt;
    private final List<Dispute> disputes = new ArrayList<>();
    private final List<CallLogEntry> callLog = new ArrayList<>();

    public CallCenter(LocalTime opensAt, LocalTime closesAt) {
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public boolean isOpenAt(LocalTime time) {
        return !time.isBefore(opensAt) && !time.isAfter(closesAt);
    }

    public String workingHours() {
        return opensAt + " - " + closesAt;
    }

    public void file(Dispute dispute) {
        disputes.add(dispute);
    }

    /** Oldest first, so cases are answered in the order they arrived. */
    public List<Dispute> waitingQueue() {
        List<Dispute> waiting = new ArrayList<>();
        for (Dispute d : disputes) {
            if (d.getStatus() == Dispute.Status.WAITING) {
                waiting.add(d);
            }
        }
        waiting.sort(Comparator.comparing(Dispute::getOpenedAt));
        return waiting;
    }

    public List<Dispute> casesOf(CallCenterAgent agent) {
        List<Dispute> cases = new ArrayList<>();
        for (Dispute d : disputes) {
            if (agent.getId().equals(d.getAgentId())) {
                cases.add(d);
            }
        }
        return cases;
    }

    public List<Dispute> disputesFor(String agreementId) {
        List<Dispute> result = new ArrayList<>();
        for (Dispute d : disputes) {
            if (d.getAgreementId().equals(agreementId)) {
                result.add(d);
            }
        }
        return result;
    }

    public Optional<Dispute> openDisputeAbout(String relatedId) {
        return disputes.stream().filter(d -> d.isOpen() && relatedId.equals(d.getRelatedId())).findFirst();
    }

    public void logCall(Dispute dispute, CallCenterAgent agent, String summary, LocalDateTime at) throws AgreementStateException {
        if (!agent.getId().equals(dispute.getAgentId())) {
            throw new AgreementStateException("Only the agent assigned to case " + dispute.getId() + " can log calls on it.");
        }
        callLog.add(new CallLogEntry(dispute.getId(), at, agent.getId(), summary));
    }

    public void restoreLog(CallLogEntry entry) {
        callLog.add(entry);
    }

    public List<CallLogEntry> logFor(Dispute dispute) {
        List<CallLogEntry> result = new ArrayList<>();
        for (CallLogEntry e : callLog) {
            if (e.getDisputeId().equals(dispute.getId())) {
                result.add(e);
            }
        }
        return result;
    }

    public List<Dispute> getDisputes() {
        return Collections.unmodifiableList(disputes);
    }

    public List<CallLogEntry> getCallLog() {
        return Collections.unmodifiableList(callLog);
    }
}
