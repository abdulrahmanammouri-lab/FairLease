package fairlease.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * One record per line, fields separated by '|'. Backslash, '|' and line breaks inside a field are escaped,
 * so chat messages and notes can contain any character.
 */
final class RecordCodec {
    private static final char SEPARATOR = '|';

    private RecordCodec() {
    }

    static String join(Object... fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                line.append(SEPARATOR);
            }
            if (fields[i] != null) {
                line.append(escape(fields[i].toString()));
            }
        }
        return line.toString();
    }

    static String[] split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                switch (next) {
                    case 'p' -> current.append(SEPARATOR);
                    case 'n' -> current.append('\n');
                    case 'r' -> current.append('\r');
                    default -> current.append(next);
                }
            } else if (c == SEPARATOR) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
