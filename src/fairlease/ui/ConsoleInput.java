package fairlease.ui;

import fairlease.exceptions.InvalidInputException;
import fairlease.model.Photo;
import fairlease.storage.PhotoVault;

import java.io.Console;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Reads everything the user types. Each method keeps asking until the answer passes its {@link InputRules} check,
 * so invalid data never reaches the rest of the program.
 */
public class ConsoleInput {
    @FunctionalInterface
    public interface Rule<T> {
        T check(String raw) throws InvalidInputException;
    }

    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleInput(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in, StandardCharsets.UTF_8);
        this.out = out;
    }

    /** Throws NoSuchElementException when the input is closed (Ctrl+Z / Ctrl+D); the application then saves and exits. */
    public String line(String prompt) {
        out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new NoSuchElementException("input closed");
        }
        return scanner.nextLine().trim();
    }

    public <T> T ask(String prompt, Rule<T> rule) {
        while (true) {
            try {
                return rule.check(line(prompt));
            } catch (InvalidInputException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    /** Like {@link #ask} but an empty answer returns null. */
    public <T> T askOptional(String prompt, Rule<T> rule) {
        while (true) {
            String raw = line(prompt);
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return rule.check(raw);
            } catch (InvalidInputException e) {
                out.println("  ! " + e.getMessage());
            }
        }
    }

    public String text(String prompt, String field, int min, int max) {
        return ask(prompt, raw -> InputRules.text(raw, field, min, max));
    }

    public int number(String prompt, String field, int min, int max) {
        return ask(prompt, raw -> InputRules.wholeNumber(raw, field, min, max));
    }

    public double money(String prompt, String field, double min, double max) {
        return ask(prompt, raw -> InputRules.money(raw, field, min, max));
    }

    public boolean yesNo(String prompt) {
        return ask(prompt + " (y/n): ", InputRules::yesNo);
    }

    public String password(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] typed = console.readPassword(prompt);
            if (typed == null) {
                throw new NoSuchElementException("input closed");
            }
            return new String(typed);
        }
        return line(prompt);
    }

    /** Shows a numbered menu; 0 is always "back". */
    public int menu(String title, String backLabel, String... options) {
        out.println();
        out.println("=== " + title + " ===");
        for (int i = 0; i < options.length; i++) {
            out.printf("  %d. %s%n", i + 1, options[i]);
        }
        out.println("  0. " + backLabel);
        return number("Choose: ", "Choice", 0, options.length);
    }

    public <E extends Enum<E>> E choice(String prompt, E[] values) {
        out.println(prompt);
        for (int i = 0; i < values.length; i++) {
            out.printf("  %d. %s%n", i + 1, values[i]);
        }
        return values[number("Choose: ", "Choice", 1, values.length) - 1];
    }

    /** Lets the user pick one element of a list by its number; returns null when the list is empty or on 0. */
    public <T> T pick(String title, List<T> options, Function<T, String> label) {
        if (options.isEmpty()) {
            out.println("  (nothing to choose from: " + title.toLowerCase() + ")");
            return null;
        }
        out.println(title + ":");
        for (int i = 0; i < options.size(); i++) {
            out.printf("  %d. %s%n", i + 1, label.apply(options.get(i)));
        }
        out.println("  0. Cancel");
        int choice = number("Choose: ", "Choice", 0, options.size());
        return choice == 0 ? null : options.get(choice - 1);
    }

    /**
     * Stands in for the in-app camera: the user gives the path of a photo, which is copied into the vault
     * with a timestamp and a fingerprint.
     */
    public Photo photo(String prompt, PhotoVault vault, boolean required) {
        while (true) {
            String raw = line(prompt + (required ? ": " : " (Enter to skip): "));
            if (raw.isEmpty()) {
                if (!required) {
                    return null;
                }
                out.println("  ! A photo is required here.");
                continue;
            }
            String path = raw.length() > 1 && raw.startsWith("\"") && raw.endsWith("\"") ? raw.substring(1, raw.length() - 1) : raw;
            try {
                Photo photo = vault.importPhoto(path, LocalDateTime.now());
                out.println("  Photo saved as " + photo.getFileName());
                return photo;
            } catch (FileNotFoundException e) {
                out.println("  ! No photo found at " + path);
            } catch (InvalidInputException e) {
                out.println("  ! " + e.getMessage());
            } catch (IOException e) {
                out.println("  ! Could not copy the photo: " + e.getMessage());
            }
        }
    }
}
