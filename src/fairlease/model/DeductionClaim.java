package fairlease.model;

import fairlease.exceptions.AgreementStateException;

/** A landlord's request to keep part of the deposit for one damaged item, backed by the move-out photo. */
public class DeductionClaim implements Disputable, PhotoEvidence {
    public enum Status { PENDING, ACCEPTED, DISPUTED, REJECTED }

    private final String id;
    private final String agreementId;
    private final String itemId;
    private double amount;
    private final String reason;
    private final Photo photo;
    private Status status;

    public DeductionClaim(String id, String agreementId, String itemId, double amount, String reason, Photo photo, Status status) {
        this.id = id;
        this.agreementId = agreementId;
        this.itemId = itemId;
        this.amount = amount;
        this.reason = reason;
        this.photo = photo;
        this.status = status;
    }

    public void accept() throws AgreementStateException {
        if (status != Status.PENDING) {
            throw new AgreementStateException("This claim was already answered (" + status + ").");
        }
        status = Status.ACCEPTED;
    }

    @Override
    public void contest(String reason) throws AgreementStateException {
        if (status != Status.PENDING) {
            throw new AgreementStateException("This claim was already answered (" + status + ").");
        }
        status = Status.DISPUTED;
    }

    @Override
    public boolean isContested() {
        return status == Status.DISPUTED;
    }

    @Override
    public void applyResolution(double agreedAmount) {
        amount = agreedAmount;
        status = agreedAmount == 0 ? Status.REJECTED : Status.ACCEPTED;
    }

    public boolean isOpen() {
        return status == Status.PENDING || status == Status.DISPUTED;
    }

    @Override
    public String describe() {
        return "Deduction " + id + ": " + reason;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public String getItemId() {
        return itemId;
    }

    @Override
    public double getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public Photo getPhoto() {
        return photo;
    }

    public Status getStatus() {
        return status;
    }
}
