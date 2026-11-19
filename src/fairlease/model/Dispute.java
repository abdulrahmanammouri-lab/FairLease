package fairlease.model;

import fairlease.exceptions.AgreementStateException;

import java.time.LocalDateTime;

/**
 * A case handled by the call center. An agent takes it, talks to both sides and proposes a resolution;
 * it is resolved only when both the tenant and the landlord accept the proposal.
 */
public class Dispute {
    public enum Topic { DEPOSIT, BILL, DAMAGE, REPAIR, BEHAVIOUR }

    public enum Status { WAITING, ASSIGNED, PROPOSED, RESOLVED, NO_AGREEMENT }

    private final String id;
    private final String agreementId;
    private final Topic topic;
    private final String openedBy;
    private final LocalDateTime openedAt;
    private final String description;
    private final String relatedId;
    private String agentId;
    private String proposal;
    private Double proposedAmount;
    private Boolean tenantAccepts;
    private Boolean landlordAccepts;
    private Status status;

    public Dispute(String id, String agreementId, Topic topic, String openedBy, LocalDateTime openedAt, String description,
                   String relatedId, String agentId, String proposal, Double proposedAmount,
                   Boolean tenantAccepts, Boolean landlordAccepts, Status status) {
        this.id = id;
        this.agreementId = agreementId;
        this.topic = topic;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        this.description = description;
        this.relatedId = relatedId;
        this.agentId = agentId;
        this.proposal = proposal;
        this.proposedAmount = proposedAmount;
        this.tenantAccepts = tenantAccepts;
        this.landlordAccepts = landlordAccepts;
        this.status = status;
    }

    public void assign(String agentId) throws AgreementStateException {
        if (status != Status.WAITING) {
            throw new AgreementStateException("Case " + id + " is already handled by another agent.");
        }
        this.agentId = agentId;
        status = Status.ASSIGNED;
    }

    public void propose(String proposal, Double amount) throws AgreementStateException {
        if (status != Status.ASSIGNED && status != Status.PROPOSED) {
            throw new AgreementStateException("A resolution can only be proposed on an assigned, open case.");
        }
        this.proposal = proposal;
        this.proposedAmount = amount;
        tenantAccepts = null;
        landlordAccepts = null;
        status = Status.PROPOSED;
    }

    /** Returns true when this answer completes the agreement of both sides. */
    public boolean answer(Role role, boolean accept) throws AgreementStateException {
        if (status != Status.PROPOSED) {
            throw new AgreementStateException("There is no proposal to answer on case " + id + ".");
        }
        if (role == Role.TENANT) {
            tenantAccepts = accept;
        } else if (role == Role.LANDLORD) {
            landlordAccepts = accept;
        } else {
            throw new AgreementStateException("Only the tenant and the landlord answer a proposal.");
        }
        if (!accept) {
            status = Status.ASSIGNED;
        } else if (Boolean.TRUE.equals(tenantAccepts) && Boolean.TRUE.equals(landlordAccepts)) {
            status = Status.RESOLVED;
        }
        return status == Status.RESOLVED;
    }

    public boolean isWaitingForAnswerFrom(Role role) {
        if (status != Status.PROPOSED) {
            return false;
        }
        return role == Role.TENANT ? tenantAccepts == null : role == Role.LANDLORD && landlordAccepts == null;
    }

    public void closeWithoutAgreement() throws AgreementStateException {
        if (!isOpen()) {
            throw new AgreementStateException("Case " + id + " is already closed.");
        }
        status = Status.NO_AGREEMENT;
    }

    public boolean isOpen() {
        return status != Status.RESOLVED && status != Status.NO_AGREEMENT;
    }

    public String getId() {
        return id;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public Topic getTopic() {
        return topic;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public String getDescription() {
        return description;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getProposal() {
        return proposal;
    }

    public Double getProposedAmount() {
        return proposedAmount;
    }

    public Boolean getTenantAccepts() {
        return tenantAccepts;
    }

    public Boolean getLandlordAccepts() {
        return landlordAccepts;
    }

    public Status getStatus() {
        return status;
    }
}
