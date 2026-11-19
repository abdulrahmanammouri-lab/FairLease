package fairlease.model;

import fairlease.exceptions.AgreementStateException;
import fairlease.exceptions.UnfairClaimException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The security deposit, held by FairLease (not by the landlord) until the move-out is settled. */
public class Deposit implements Payable {
    public enum Status { NOT_PAID, HELD, UNDER_REVIEW, RETURNED }

    private final String agreementId;
    private final double amount;
    private Status status;
    private LocalDate paidOn;
    private double returnedAmount;
    private LocalDate returnedOn;
    private final List<DeductionClaim> claims = new ArrayList<>();

    public Deposit(String agreementId, double amount, Status status, LocalDate paidOn, double returnedAmount, LocalDate returnedOn) {
        this.agreementId = agreementId;
        this.amount = amount;
        this.status = status;
        this.paidOn = paidOn;
        this.returnedAmount = returnedAmount;
        this.returnedOn = returnedOn;
    }

    @Override
    public String getId() {
        return "DEP-" + agreementId;
    }

    @Override
    public String describe() {
        return "Security deposit";
    }

    @Override
    public double getAmountDue() {
        return status == Status.NOT_PAID ? amount : 0;
    }

    @Override
    public boolean isPayable() {
        return status == Status.NOT_PAID;
    }

    @Override
    public void markPaid(LocalDate date) {
        status = Status.HELD;
        paidOn = date;
    }

    public void addClaim(DeductionClaim claim) throws UnfairClaimException {
        if (status != Status.HELD && status != Status.UNDER_REVIEW) {
            throw new UnfairClaimException("The deposit is " + status + ", so nothing can be deducted from it.");
        }
        double alreadyClaimed = claims.stream()
                .filter(c -> c.getStatus() != DeductionClaim.Status.REJECTED)
                .mapToDouble(DeductionClaim::getAmount)
                .sum();
        if (alreadyClaimed + claim.getAmount() > amount) {
            throw new UnfairClaimException("Claims would total " + (alreadyClaimed + claim.getAmount())
                    + " but the deposit is only " + amount + ".");
        }
        claims.add(claim);
        status = Status.UNDER_REVIEW;
    }

    public void restoreClaim(DeductionClaim claim) {
        claims.add(claim);
    }

    public boolean hasOpenClaims() {
        return claims.stream().anyMatch(DeductionClaim::isOpen);
    }

    public double acceptedDeductions() {
        return claims.stream()
                .filter(c -> c.getStatus() == DeductionClaim.Status.ACCEPTED)
                .mapToDouble(DeductionClaim::getAmount)
                .sum();
    }

    public double settle(double unpaidBills, LocalDate today) throws AgreementStateException {
        if (status == Status.NOT_PAID) {
            throw new AgreementStateException("The deposit was never paid.");
        }
        if (status == Status.RETURNED) {
            throw new AgreementStateException("The deposit was already returned on " + returnedOn + ".");
        }
        if (hasOpenClaims()) {
            throw new AgreementStateException("Some deduction claims are still waiting for the tenant or the call center.");
        }
        double remaining = amount - acceptedDeductions();
        if (unpaidBills > remaining) {
            throw new AgreementStateException("Unpaid bills (" + unpaidBills + ") exceed what is left of the deposit ("
                    + remaining + "); the tenant must pay them first.");
        }
        returnedAmount = remaining - unpaidBills;
        status = Status.RETURNED;
        returnedOn = today;
        return returnedAmount;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public double getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public double getReturnedAmount() {
        return returnedAmount;
    }

    public LocalDate getReturnedOn() {
        return returnedOn;
    }

    public List<DeductionClaim> getClaims() {
        return Collections.unmodifiableList(claims);
    }
}
