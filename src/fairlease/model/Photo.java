package fairlease.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** A photo stored in the FairLease vault. The SHA-256 hash proves the file was not edited after it was taken. */
public final class Photo {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String fileName;
    private final String sha256;
    private final LocalDateTime takenAt;

    public Photo(String fileName, String sha256, LocalDateTime takenAt) {
        this.fileName = fileName;
        this.sha256 = sha256;
        this.takenAt = takenAt;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSha256() {
        return sha256;
    }

    public LocalDateTime getTakenAt() {
        return takenAt;
    }

    @Override
    public String toString() {
        return fileName + " (taken " + takenAt.format(STAMP) + ")";
    }
}
