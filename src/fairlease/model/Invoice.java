package fairlease.model;

import fairlease.exceptions.AgreementStateException;

import java.time.LocalDate;
import java.time.YearMonth;

/** A monthly rent invoice (generated automatically) or a utility bill uploaded by the landlord with a photo. */
public class Invoice implements Payable, Disputable, PhotoEvidence {
    public enum Status { UNPAID, ON_HOLD, PAID }

    private final String id;
    private final String agreementId;
    private final BillType type;
    private final YearMonth period;
    private double amount;
    private final Photo photo;
    private Status status;
    private String contestReason;
    private LocalDate paidOn;

    public Invoice(String id, String agreementId, BillType type, YearMonth period, double amount, Photo photo,
                   Status status, String contestReason, LocalDate paidOn) {
        this.id = id;
        this.agreementId = agreementId;
        this.type = type;
        this.period = period;
        this.amount = amount;
        this.photo = photo;
        this.status = status;
        this.contestReason = contestReason;
        this.paidOn = paidOn;
    }

    @Override
    public String describe() {
        return type + " " + period;
    }

    @Override
    public double getAmountDue() {
        return status == Status.PAID ? 0 : amount;
    }

    @Override
    public boolean isPayable() {
        return status == Status.UNPAID;
    }

    @Override
    public void markPaid(LocalDate date) {
        status = Status.PAID;
        paidOn = date;
    }

    @Override
    public void contest(String reason) throws AgreementStateException {
        if (status != Status.UNPAID) {
            throw new AgreementStateException("Only an unpaid bill can be contested (this one is " + status + ").");
        }
        status = Status.ON_HOLD;
        contestReason = reason;
    }

    @Override
    public boolean isContested() {
        return status == Status.ON_HOLD;
    }

    @Override
    public void applyResolution(double agreedAmount) {
        amount = agreedAmount;
        if (agreedAmount == 0) {
            markPaid(LocalDate.now());
        } else {
            status = Status.UNPAID;
        }
    }

    public boolean isOverdue(LocalDate dueDate, LocalDate today) {
        return status == Status.UNPAID && today.isAfter(dueDate);
    }

    @Override
    public String getId() {
        return id;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public BillType getType() {
        return type;
    }

    public YearMonth getPeriod() {
        return period;
    }

    @Override
    public double getAmount() {
        return amount;
    }

    @Override
    public Photo getPhoto() {
        return photo;
    }

    public Status getStatus() {
        return status;
    }

    public String getContestReason() {
        return contestReason;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }
}
