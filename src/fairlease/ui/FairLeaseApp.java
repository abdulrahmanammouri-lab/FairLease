package fairlease.ui;

import fairlease.config.AppConfig;
import fairlease.exceptions.InvalidInputException;
import fairlease.model.CallCenterAgent;
import fairlease.model.Landlord;
import fairlease.model.Role;
import fairlease.model.Tenant;
import fairlease.model.User;
import fairlease.service.FairLease;
import fairlease.storage.FairLeaseFileStore;
import fairlease.storage.PhotoVault;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** The start screen: log in, register, and hand over to the menu of the user's role. */
public class FairLeaseApp {
    private static final int LOGIN_ATTEMPTS = 3;

    private final FairLease lease;
    private final FairLeaseFileStore store;
    private final PhotoVault vault;
    private final ConsoleInput input;
    private final Views views;
    private final PrintStream out;

    public FairLeaseApp(FairLease lease, FairLeaseFileStore store, PhotoVault vault, InputStream in, PrintStream out) {
        this.lease = lease;
        this.store = store;
        this.vault = vault;
        this.out = out;
        this.input = new ConsoleInput(in, out);
        this.views = new Views(lease, vault, out);
    }

    public void run() {
        out.println("FairLease - protecting tenants and landlords from move-in to move-out.");
        out.println("Data folder: " + store.getFolder().toAbsolutePath());
        try {
            while (true) {
                int choice = input.menu("FairLease", "Exit", "Log in", "Create an account", "How FairLease works");
                switch (choice) {
                    case 0 -> {
                        save();
                        out.println("Goodbye!");
                        return;
                    }
                    case 1 -> login();
                    case 2 -> register();
                    case 3 -> about();
                    default -> { }
                }
            }
        } catch (NoSuchElementException e) {
            out.println();
            out.println("Input closed: saving and exiting.");
            save();
        }
    }

    private void login() {
        for (int attempt = 1; attempt <= LOGIN_ATTEMPTS; attempt++) {
            String email = input.askOptional("E-mail (Enter to go back): ", InputRules::email);
            if (email == null) {
                return;
            }
            String password = input.password("Password: ");
            Optional<User> user = lease.login(email, password);
            if (user.isPresent()) {
                openSession(user.get());
                return;
            }
            out.println("  ! Wrong e-mail or password (" + (LOGIN_ATTEMPTS - attempt) + " attempt(s) left).");
        }
    }

    private void openSession(User user) {
        out.println();
        out.println("Welcome, " + user.getName() + " (" + user.getRole() + ").");
        List<String> changes = lease.applyAutomaticRules(LocalDateTime.now());
        changes.forEach(c -> out.println("  * " + c));
        save();
        out.println("Notifications:");
        views.notifications(user, LocalDate.now());
        if (user instanceof Tenant tenant) {
            new TenantMenu(this, tenant).run();
        } else if (user instanceof Landlord landlord) {
            new LandlordMenu(this, landlord).run();
        } else if (user instanceof CallCenterAgent agent) {
            new AgentMenu(this, agent).run();
        }
        out.println("Logged out.");
    }

    private void register() {
        Role role = input.choice("Account type:", Role.values());
        String name = input.ask("Full name: ", InputRules::personName);
        String email = input.ask("E-mail: ", raw -> {
            String value = InputRules.email(raw);
            if (lease.findByEmail(value).isPresent()) {
                throw new InvalidInputException("An account with this e-mail already exists.");
            }
            return value;
        });
        String phone = input.ask("Phone: ", InputRules::phone);
        String password = askNewPassword();
        try {
            User user = switch (role) {
                case TENANT -> lease.registerTenant(name, email, phone, password);
                case LANDLORD -> lease.registerLandlord(name, email, phone, password,
                        input.ask("IBAN where you receive the rent: ", InputRules::iban));
                case AGENT -> lease.registerAgent(name, email, phone, password,
                        input.text("Agent registration code: ", "Code", 1, 40));
            };
            save();
            out.println("  Account created. You can log in as " + user.getEmail() + ".");
        } catch (InvalidInputException e) {
            out.println("  ! " + e.getMessage());
        }
    }

    private String askNewPassword() {
        while (true) {
            String password = input.password("Password (at least 6 characters, letters and digits): ");
            try {
                InputRules.password(password);
            } catch (InvalidInputException e) {
                out.println("  ! " + e.getMessage());
                continue;
            }
            if (password.equals(input.password("Repeat the password: "))) {
                return password;
            }
            out.println("  ! The two passwords are different.");
        }
    }

    private void about() {
        out.println();
        out.println("How FairLease works");
        out.println("  1. The landlord adds the property and the inventory of every room, then sends a digital agreement.");
        out.println("  2. The tenant signs and pays the deposit, which FairLease holds (not the landlord).");
        out.println("  3. Before the keys are handed over, the tenant photographs every item and meter; both sides confirm the report.");
        out.println("  4. Every month, rent and bills (with photos of the original bills) arrive in one statement, paid at once.");
        out.println("  5. At move-out the same photo walk is repeated and compared item by item with move-in.");
        out.println("  6. Only NEW damage, proven by photos, can be deducted from the deposit. The rest is returned automatically.");
        out.println("  7. Disagreements go to the FairLease call center, which mediates and proposes a fair resolution.");
    }

    public void save() {
        try {
            store.save(lease);
        } catch (IOException e) {
            out.println("  ! Could not save the data: " + e.getMessage());
        }
    }

    public void saveDocument(String agreementId, String fileName, String content) {
        try {
            Path file = store.writeDocument(agreementId, fileName, content);
            out.println("  Document saved: " + file);
        } catch (IOException e) {
            out.println("  ! Could not write the document: " + e.getMessage());
        }
    }

    FairLease getLease() {
        return lease;
    }

    AppConfig getConfig() {
        return lease.getConfig();
    }

    ConsoleInput getInput() {
        return input;
    }

    Views getViews() {
        return views;
    }

    PhotoVault getVault() {
        return vault;
    }

    PrintStream getOut() {
        return out;
    }
}
