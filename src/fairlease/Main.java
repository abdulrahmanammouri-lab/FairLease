package fairlease;

import fairlease.config.AppConfig;
import fairlease.exceptions.AgreementStateException;
import fairlease.exceptions.InvalidInputException;
import fairlease.service.FairLease;
import fairlease.storage.FairLeaseFileStore;
import fairlease.storage.PhotoVault;
import fairlease.ui.FairLeaseApp;
import fairlease.ui.Views;

import java.io.IOException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromArgs(args);
        } catch (InvalidInputException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(AppConfig.usage());
            System.exit(2);
            return;
        }
        if (config.getMode() == AppConfig.Mode.HELP) {
            System.out.println(AppConfig.usage());
            return;
        }
        System.out.println("Configuration: " + config.getConfigFile() + " (" + config.getMode() + " mode)");
        config.getWarnings().forEach(w -> System.out.println("  config warning: " + w));

        FairLease lease = new FairLease(config);
        FairLeaseFileStore store = new FairLeaseFileStore(config.getDataDir());
        PhotoVault vault = new PhotoVault(config.getDataDir().resolve("photos"), config.getMaxPhotoBytes());
        store.load(lease);
        store.getWarnings().forEach(w -> System.out.println("  data warning: " + w));

        if (config.getMode() == AppConfig.Mode.DEMO && lease.getUsers().isEmpty()) {
            try {
                DemoData.seed(lease, vault, config.getDataDir().resolve("sample-photos"));
                store.save(lease);
                System.out.println("Demo data created. Accounts: maria@fairlease.ro / landlord1, andrei@student.ro / tenant1,");
                System.out.println("  ioana@student.ro / tenant1, agent@fairlease.ro / agent1.");
                System.out.println("  Sample photos: " + config.getDataDir().resolve("sample-photos").toAbsolutePath());
            } catch (IOException | InvalidInputException | AgreementStateException e) {
                System.err.println("Could not create the demo data: " + e.getMessage());
            }
        }

        if (config.getMode() == AppConfig.Mode.REPORT) {
            new Views(lease, vault, System.out).report();
            return;
        }
        new FairLeaseApp(lease, store, vault, System.in, System.out).run();
    }
}
