package fairlease.config;

import fairlease.exceptions.InvalidInputException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Settings read from a .properties file and overridden by program arguments.
 * Missing or broken values never stop the program: they fall back to defaults and produce a warning.
 */
public final class AppConfig {
    public enum Mode { INTERACTIVE, DEMO, REPORT, HELP }

    public enum PhotoPolicy { ALL_ITEMS, DAMAGED_ONLY }

    private static final Path DEFAULT_CONFIG = Paths.get("config", "fairlease.properties");
    private static final Path DEMO_CONFIG = Paths.get("config", "demo.properties");

    private Mode mode = Mode.INTERACTIVE;
    private Path configFile;
    private Path dataDir = Paths.get("data");
    private String currency = "RON";
    private int inspectionResponseHours = 72;
    private int depositReturnDays = 14;
    private int repairEscalationHours = 24;
    private LocalTime callCenterOpens = LocalTime.of(9, 0);
    private LocalTime callCenterCloses = LocalTime.of(18, 0);
    private PhotoPolicy photoPolicy = PhotoPolicy.ALL_ITEMS;
    private long maxPhotoBytes = 10L * 1024 * 1024;
    private String agentCode = "FL-AGENT-2026";
    private final List<String> warnings = new ArrayList<>();

    private AppConfig() {
    }

    public static AppConfig fromArgs(String[] args) throws InvalidInputException {
        AppConfig config = new AppConfig();
        Path dataOverride = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> {
                    config.configFile = toPath(valueAfter(args, i));
                    i++;
                }
                case "--data" -> {
                    dataOverride = toPath(valueAfter(args, i));
                    i++;
                }
                case "--demo" -> config.mode = Mode.DEMO;
                case "--report" -> config.mode = Mode.REPORT;
                case "--help", "-h" -> config.mode = Mode.HELP;
                default -> throw new InvalidInputException("Unknown argument: " + args[i]);
            }
        }
        if (config.configFile == null) {
            config.configFile = config.mode == Mode.DEMO ? DEMO_CONFIG : DEFAULT_CONFIG;
        }
        if (config.mode != Mode.HELP) {
            config.loadFile();
        }
        if (dataOverride != null) {
            config.dataDir = dataOverride;
        }
        return config;
    }

    public static String usage() {
        return String.join(System.lineSeparator(),
                "Usage: java -cp out fairlease.Main [options]",
                "  (no options)        interactive mode with config/fairlease.properties",
                "  --demo              interactive mode with config/demo.properties and sample data",
                "  --report            print a summary of every rental and exit",
                "  --config <file>     use another configuration file",
                "  --data <folder>     store the data in another folder",
                "  --help              show this message");
    }

    private static String valueAfter(String[] args, int index) throws InvalidInputException {
        if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
            throw new InvalidInputException(args[index] + " must be followed by a value.");
        }
        return args[index + 1];
    }

    private static Path toPath(String raw) throws InvalidInputException {
        try {
            return Paths.get(raw);
        } catch (InvalidPathException e) {
            throw new InvalidInputException("'" + raw + "' is not a valid path: " + e.getReason());
        }
    }

    private void loadFile() {
        Properties props = new Properties();
        try (Reader reader = new FileReader(configFile.toFile(), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (FileNotFoundException e) {
            warnings.add("Configuration file " + configFile + " not found; using default settings.");
            return;
        } catch (IOException e) {
            warnings.add("Could not read " + configFile + " (" + e.getMessage() + "); using default settings.");
            return;
        }

        String dir = props.getProperty("data.dir");
        if (dir != null) {
            try {
                dataDir = toPath(dir.trim());
            } catch (InvalidInputException e) {
                warnings.add("data.dir: " + e.getMessage() + " Using " + dataDir + ".");
            }
        }
        String cur = props.getProperty("currency", currency).trim().toUpperCase(Locale.ROOT);
        if (cur.matches("[A-Z]{3}")) {
            currency = cur;
        } else {
            warnings.add("currency=" + cur + " is not a 3-letter code; using " + currency + ".");
        }
        inspectionResponseHours = intSetting(props, "inspection.response.hours", inspectionResponseHours, 1, 24 * 30);
        depositReturnDays = intSetting(props, "deposit.return.days", depositReturnDays, 0, 90);
        repairEscalationHours = intSetting(props, "repair.escalation.hours", repairEscalationHours, 1, 24 * 14);
        maxPhotoBytes = intSetting(props, "photo.max.mb", 10, 1, 100) * 1024L * 1024L;

        LocalTime opens = timeSetting(props, "callcenter.opens", callCenterOpens);
        LocalTime closes = timeSetting(props, "callcenter.closes", callCenterCloses);
        if (closes.isAfter(opens)) {
            callCenterOpens = opens;
            callCenterCloses = closes;
        } else {
            warnings.add("callcenter.closes must be after callcenter.opens; using "
                    + callCenterOpens + "-" + callCenterCloses + ".");
        }

        String policy = props.getProperty("inspection.photos");
        if (policy != null) {
            try {
                photoPolicy = PhotoPolicy.valueOf(policy.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add("inspection.photos=" + policy + " is not ALL_ITEMS or DAMAGED_ONLY; using " + photoPolicy + ".");
            }
        }
        String code = props.getProperty("agent.registration.code");
        if (code != null && !code.isBlank()) {
            agentCode = code.trim();
        }
    }

    private int intSetting(Properties props, String key, int fallback, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                warnings.add(key + "=" + value + " must be between " + min + " and " + max + "; using " + fallback + ".");
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            warnings.add(key + "=" + raw + " is not a whole number; using " + fallback + ".");
            return fallback;
        }
    }

    private LocalTime timeSetting(Properties props, String key, LocalTime fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            warnings.add(key + "=" + raw + " is not a time like 09:00; using " + fallback + ".");
            return fallback;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public Path getConfigFile() {
        return configFile;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public String getCurrency() {
        return currency;
    }

    public int getInspectionResponseHours() {
        return inspectionResponseHours;
    }

    public int getDepositReturnDays() {
        return depositReturnDays;
    }

    public int getRepairEscalationHours() {
        return repairEscalationHours;
    }

    public LocalTime getCallCenterOpens() {
        return callCenterOpens;
    }

    public LocalTime getCallCenterCloses() {
        return callCenterCloses;
    }

    public PhotoPolicy getPhotoPolicy() {
        return photoPolicy;
    }

    public long getMaxPhotoBytes() {
        return maxPhotoBytes;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
