package fairlease.storage;

import fairlease.exceptions.InvalidInputException;
import fairlease.model.Photo;
import fairlease.model.PhotoEvidence;
import fairlease.util.Hashing;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * Stores evidence photos inside the data folder. Each photo is copied, stamped with the time it was added,
 * made read-only and fingerprinted, so any later edit of the file is detected.
 */
public class PhotoVault {
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path folder;
    private final long maxBytes;

    public PhotoVault(Path folder, long maxBytes) {
        this.folder = folder;
        this.maxBytes = maxBytes;
    }

    public Photo importPhoto(String sourcePath, LocalDateTime takenAt) throws FileNotFoundException, IOException, InvalidInputException {
        Path source;
        try {
            source = Paths.get(sourcePath);
        } catch (InvalidPathException e) {
            throw new InvalidInputException("'" + sourcePath + "' is not a valid file path.");
        }
        if (!Files.isRegularFile(source)) {
            throw new FileNotFoundException(sourcePath);
        }
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new InvalidInputException("Only .jpg, .jpeg and .png photos are accepted.");
        }
        long size = Files.size(source);
        if (size == 0 || size > maxBytes) {
            throw new InvalidInputException("The photo must be between 1 byte and " + maxBytes / (1024 * 1024) + " MB.");
        }
        Files.createDirectories(folder);
        String safeName = name.substring(0, dot).replaceAll("[^A-Za-z0-9_-]", "_");
        Path target = folder.resolve(takenAt.format(FILE_STAMP) + "_" + safeName + "." + extension);
        Files.copy(source, target);
        target.toFile().setReadOnly();
        return new Photo(target.getFileName().toString(), Hashing.sha256(Files.readAllBytes(target)), takenAt);
    }

    public boolean isIntact(Photo photo) {
        try {
            return Hashing.sha256(Files.readAllBytes(folder.resolve(photo.getFileName()))).equals(photo.getSha256());
        } catch (IOException e) {
            return false;
        }
    }

    public String describe(PhotoEvidence evidence) {
        if (!evidence.hasPhoto()) {
            return "no photo";
        }
        Photo photo = evidence.getPhoto();
        return "photo " + photo + (isIntact(photo) ? "" : "  ** FILE MISSING OR MODIFIED AFTER UPLOAD **");
    }

    public Path getFolder() {
        return folder;
    }
}
