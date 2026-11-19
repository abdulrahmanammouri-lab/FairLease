package fairlease.model;

/** Anything in a rental that can be backed by a timestamped photo: inspection entries, bills, deduction claims, repairs. */
public interface PhotoEvidence {
    Photo getPhoto();

    default boolean hasPhoto() {
        return getPhoto() != null;
    }
}
