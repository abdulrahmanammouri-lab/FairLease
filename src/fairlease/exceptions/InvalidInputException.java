package fairlease.exceptions;

/**
 * A value typed by the user, or passed as a program argument, that breaks a FairLease rule
 * (wrong e-mail, negative rent, invalid IBAN, unknown argument...).
 */
public class InvalidInputException extends Exception {
    public InvalidInputException(String message) {
        super(message);
    }
}
