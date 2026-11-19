package fairlease.exceptions;

/**
 * A deduction from the security deposit that FairLease refuses because it would be unfair to the tenant:
 * the damage was already recorded at move-in, there is no photo evidence, the deadline has passed,
 * or the landlord asks for more than the deposit.
 */
public class UnfairClaimException extends Exception {
    public UnfairClaimException(String message) {
        super(message);
    }
}
