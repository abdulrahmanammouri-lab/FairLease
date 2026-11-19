package fairlease.model;

import java.time.LocalDate;

/** Something the tenant pays through FairLease: the monthly rent, a utility bill or the security deposit. */
public interface Payable {
    String getId();

    String describe();

    double getAmountDue();

    boolean isPayable();

    void markPaid(LocalDate date);
}
