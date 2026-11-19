package fairlease.model;

import fairlease.exceptions.AgreementStateException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Agreement {
    public enum Status { PENDING, ACTIVE, REJECTED, ENDED }

    private final String id;
    private final String propertyId;
    private final String landlordId;
    private final String tenantId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final double monthlyRent;
    private final int dueDay;
    private final double depositAmount;
    private final Set<BillType> includedBills;
    private final String houseRules;
    private Status status;
    private LocalDateTime signedAt;

    public Agreement(String id, String propertyId, String landlordId, String tenantId,
                     LocalDate startDate, LocalDate endDate, double monthlyRent, int dueDay, double depositAmount,
                     Set<BillType> includedBills, String houseRules, Status status, LocalDateTime signedAt) {
        this.id = id;
        this.propertyId = propertyId;
        this.landlordId = landlordId;
        this.tenantId = tenantId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.monthlyRent = monthlyRent;
        this.dueDay = dueDay;
        this.depositAmount = depositAmount;
        this.includedBills = includedBills.isEmpty() ? EnumSet.noneOf(BillType.class) : EnumSet.copyOf(includedBills);
        this.houseRules = houseRules;
        this.status = status;
        this.signedAt = signedAt;
    }

    public void sign(LocalDateTime when) throws AgreementStateException {
        requirePending();
        status = Status.ACTIVE;
        signedAt = when;
    }

    public void reject() throws AgreementStateException {
        requirePending();
        status = Status.REJECTED;
    }

    public void end() {
        status = Status.ENDED;
    }

    private void requirePending() throws AgreementStateException {
        if (status != Status.PENDING) {
            throw new AgreementStateException("Only a pending agreement can be signed or rejected (this one is " + status + ").");
        }
    }

    public boolean includes(BillType type) {
        return includedBills.contains(type);
    }

    public boolean covers(YearMonth month) {
        return !month.isBefore(YearMonth.from(startDate)) && !month.isAfter(YearMonth.from(endDate));
    }

    public LocalDate dueDate(YearMonth month) {
        return month.atDay(dueDay);
    }

    public boolean isParty(User user) {
        return user.getId().equals(tenantId) || user.getId().equals(landlordId);
    }

    public String getId() {
        return id;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getLandlordId() {
        return landlordId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public double getMonthlyRent() {
        return monthlyRent;
    }

    public int getDueDay() {
        return dueDay;
    }

    public double getDepositAmount() {
        return depositAmount;
    }

    public Set<BillType> getIncludedBills() {
        return Collections.unmodifiableSet(includedBills);
    }

    public String getHouseRules() {
        return houseRules;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }
}
