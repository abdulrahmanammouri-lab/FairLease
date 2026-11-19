package fairlease.ui;

import fairlease.exceptions.InvalidInputException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Every rule a typed value must pass before it reaches the rest of the program. */
public final class InputRules {
    private static final Pattern NAME = Pattern.compile("\\p{L}[\\p{L} .'-]{1,59}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");
    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    private static final Pattern EXPIRY = Pattern.compile("(0[1-9]|1[0-2])/([0-9]{2})");

    private InputRules() {
    }

    public static String text(String raw, String field, int min, int max) throws InvalidInputException {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < min) {
            throw new InvalidInputException(min <= 1 ? field + " cannot be empty." : field + " needs at least " + min + " characters.");
        }
        if (value.length() > max) {
            throw new InvalidInputException(field + " can have at most " + max + " characters.");
        }
        for (char c : value.toCharArray()) {
            if (Character.isISOControl(c)) {
                throw new InvalidInputException(field + " contains invalid characters.");
            }
        }
        return value;
    }

    public static String personName(String raw) throws InvalidInputException {
        String value = text(raw, "Name", 2, 60);
        if (!NAME.matcher(value).matches()) {
            throw new InvalidInputException("A name can contain only letters, spaces, '.', '-' and apostrophes.");
        }
        return value;
    }

    public static String email(String raw) throws InvalidInputException {
        String value = text(raw, "E-mail", 1, 100).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(value).matches()) {
            throw new InvalidInputException("'" + value + "' is not a valid e-mail address (example: name@domain.com).");
        }
        return value;
    }

    public static String phone(String raw) throws InvalidInputException {
        String value = text(raw, "Phone", 1, 25).replaceAll("[\\s()-]", "");
        if (!PHONE.matcher(value).matches()) {
            throw new InvalidInputException("A phone number has 9 to 15 digits, optionally starting with + (example: +40722123456).");
        }
        return value;
    }

    public static String password(String raw) throws InvalidInputException {
        if (raw == null || raw.length() < 6 || raw.length() > 64) {
            throw new InvalidInputException("The password must have between 6 and 64 characters.");
        }
        if (raw.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidInputException("The password cannot contain spaces.");
        }
        if (raw.chars().noneMatch(Character::isLetter) || raw.chars().noneMatch(Character::isDigit)) {
            throw new InvalidInputException("The password needs at least one letter and one digit.");
        }
        return raw;
    }

    /** Checks the format and the ISO 13616 mod-97 checksum, which catches almost every typing mistake. */
    public static String iban(String raw) throws InvalidInputException {
        String value = text(raw, "IBAN", 1, 40).replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IBAN.matcher(value).matches()) {
            throw new InvalidInputException("An IBAN starts with a country code and 2 digits (example: RO49AAAA1B31007593840000).");
        }
        String rearranged = value.substring(4) + value.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        if (!new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).equals(BigInteger.ONE)) {
            throw new InvalidInputException("This IBAN has a wrong checksum; please check it for typos.");
        }
        return value;
    }

    public static int wholeNumber(String raw, String field, int min, int max) throws InvalidInputException {
        String value = text(raw, field, 1, 12);
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) {
                throw new InvalidInputException(field + " must be between " + min + " and " + max + ".");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field + " must be a whole number, not '" + value + "'.");
        }
    }

    /** Amounts are checked with BigDecimal so values like 10.555 or 1e5 are refused instead of silently rounded. */
    public static double decimal(String raw, String field, double min, double max, int maxDecimals) throws InvalidInputException {
        String value = text(raw, field, 1, 20).replace(',', '.');
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field + " must be a number, not '" + value + "'.");
        }
        if (value.toLowerCase(Locale.ROOT).contains("e")) {
            throw new InvalidInputException(field + " must be written as a plain number.");
        }
        if (number.scale() > maxDecimals) {
            throw new InvalidInputException(field + " can have at most " + maxDecimals + " decimals.");
        }
        double result = number.doubleValue();
        if (result < min || result > max) {
            throw new InvalidInputException(field + " must be between " + min + " and " + max + ".");
        }
        return result;
    }

    public static double money(String raw, String field, double min, double max) throws InvalidInputException {
        return decimal(raw, field, min, max, 2);
    }

    public static LocalDate date(String raw, String field) throws InvalidInputException {
        String value = text(raw, field, 1, 10);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(field + " must be a real date written as YYYY-MM-DD (example: 2026-09-01).");
        }
    }

    public static YearMonth month(String raw, String field) throws InvalidInputException {
        String value = text(raw, field, 1, 7);
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(field + " must be written as YYYY-MM (example: 2026-10).");
        }
    }

    public static boolean yesNo(String raw) throws InvalidInputException {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "y", "yes", "da" -> true;
            case "n", "no", "nu" -> false;
            default -> throw new InvalidInputException("Please answer y or n.");
        };
    }

    /** Card numbers are checked with the Luhn algorithm; they are never stored. */
    public static String cardNumber(String raw) throws InvalidInputException {
        String digits = text(raw, "Card number", 1, 30).replaceAll("[\\s-]", "");
        if (!digits.matches("[0-9]{13,19}")) {
            throw new InvalidInputException("A card number has 13 to 19 digits.");
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        if (sum % 10 != 0) {
            throw new InvalidInputException("This card number is not valid; please check the digits.");
        }
        return digits;
    }

    public static YearMonth cardExpiry(String raw, LocalDate today) throws InvalidInputException {
        var matcher = EXPIRY.matcher(text(raw, "Expiry", 1, 5));
        if (!matcher.matches()) {
            throw new InvalidInputException("Write the expiry date as MM/YY (example: 08/28).");
        }
        YearMonth expiry = YearMonth.of(2000 + Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
        if (expiry.isBefore(YearMonth.from(today))) {
            throw new InvalidInputException("This card expired in " + expiry + ".");
        }
        return expiry;
    }

    public static String cvv(String raw) throws InvalidInputException {
        String value = text(raw, "CVV", 1, 4);
        if (!value.matches("[0-9]{3,4}")) {
            throw new InvalidInputException("The CVV is the 3 or 4 digits on the back of the card.");
        }
        return value;
    }
}
