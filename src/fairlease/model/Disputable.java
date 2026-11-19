package fairlease.model;

import fairlease.exceptions.AgreementStateException;

/**
 * Something one side of the rental can contest and send to the call center: a bill or a deposit deduction.
 * When both sides accept the agent's proposal, the agreed amount is applied automatically.
 */
public interface Disputable {
    String getId();

    String describe();

    double getAmount();

    void contest(String reason) throws AgreementStateException;

    boolean isContested();

    void applyResolution(double agreedAmount);
}
