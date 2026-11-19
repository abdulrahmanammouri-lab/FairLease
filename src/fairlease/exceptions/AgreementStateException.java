package fairlease.exceptions;

/**
 * The rental is not in a state that allows the requested action, e.g. inspecting an unsigned agreement,
 * paying a bill that is on hold, or uploading a bill that is already included in the rent.
 */
public class AgreementStateException extends Exception {
    public AgreementStateException(String message) {
        super(message);
    }
}
