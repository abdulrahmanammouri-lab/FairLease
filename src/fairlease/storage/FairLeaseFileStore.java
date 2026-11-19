package fairlease.storage;

import fairlease.model.Agreement;
import fairlease.model.BillType;
import fairlease.model.CallCenterAgent;
import fairlease.model.CallLogEntry;
import fairlease.model.Condition;
import fairlease.model.DeductionClaim;
import fairlease.model.Deposit;
import fairlease.model.Dispute;
import fairlease.model.Inspection;
import fairlease.model.InspectionEntry;
import fairlease.model.Invoice;
import fairlease.model.Item;
import fairlease.model.Landlord;
import fairlease.model.MaintenanceRequest;
import fairlease.model.Message;
import fairlease.model.Payment;
import fairlease.model.Photo;
import fairlease.model.Property;
import fairlease.model.Role;
import fairlease.model.Tenant;
import fairlease.model.User;
import fairlease.service.FairLease;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Saves the whole FairLease state as readable text files (one per kind of record) and loads it back.
 * A damaged line is skipped with a warning instead of stopping the program.
 */
public class FairLeaseFileStore {
    private static final String USERS = "users.txt";
    private static final String PROPERTIES = "properties.txt";
    private static final String ITEMS = "items.txt";
    private static final String AGREEMENTS = "agreements.txt";
    private static final String INSPECTIONS = "inspections.txt";
    private static final String ENTRIES = "inspection_entries.txt";
    private static final String INVOICES = "invoices.txt";
    private static final String PAYMENTS = "payments.txt";
    private static final String DEPOSITS = "deposits.txt";
    private static final String CLAIMS = "claims.txt";
    private static final String MESSAGES = "messages.txt";
    private static final String REPAIRS = "repairs.txt";
    private static final String DISPUTES = "disputes.txt";
    private static final String CALL_LOG = "call_log.txt";

    private final Path folder;
    private final List<String> warnings = new ArrayList<>();

    public FairLeaseFileStore(Path folder) {
        this.folder = folder;
    }

    // ---------------------------------------------------------------- load

    public void load(FairLease lease) {
        warnings.clear();
        read(USERS, f -> lease.restore(parseUser(f)));
        read(PROPERTIES, f -> lease.restore(new Property(f[0], f[1], Property.Type.valueOf(f[2]), f[3],
                Double.parseDouble(f[4]), Property.Status.valueOf(f[5]))));
        read(ITEMS, f -> lease.restoreItem(new Item(f[0], f[1], f[2], f[3], Item.Category.valueOf(f[4]))));
        read(AGREEMENTS, f -> lease.restore(new Agreement(f[0], f[1], f[2], f[3], LocalDate.parse(f[4]), LocalDate.parse(f[5]),
                Double.parseDouble(f[6]), Integer.parseInt(f[7]), Double.parseDouble(f[8]), parseBills(f[9]), f[10],
                Agreement.Status.valueOf(f[11]), dateTime(f[12]))));
        read(INSPECTIONS, f -> lease.restore(new Inspection(f[0], f[1], Inspection.Type.valueOf(f[2]), dateTime(f[3]),
                Boolean.parseBoolean(f[4]), text(f[5]))));
        read(ENTRIES, f -> lease.restoreEntry(f[0], new InspectionEntry(f[1], Condition.valueOf(f[2]), text(f[3]),
                decimal(f[4]), photo(f, 5))));
        read(INVOICES, f -> lease.restore(new Invoice(f[0], f[1], BillType.valueOf(f[2]), YearMonth.parse(f[3]),
                Double.parseDouble(f[4]), photo(f, 8), Invoice.Status.valueOf(f[5]), text(f[6]), date(f[7]))));
        read(PAYMENTS, f -> lease.restore(new Payment(f[0], f[1], LocalDate.parse(f[2]), Double.parseDouble(f[3]),
                Payment.Method.valueOf(f[4]), f[5].isEmpty() ? List.of() : Arrays.asList(f[5].split(",")))));
        read(DEPOSITS, f -> lease.restore(new Deposit(f[0], Double.parseDouble(f[1]), Deposit.Status.valueOf(f[2]),
                date(f[3]), Double.parseDouble(f[4]), date(f[5]))));
        read(CLAIMS, f -> lease.restoreClaim(new DeductionClaim(f[0], f[1], f[2], Double.parseDouble(f[3]), f[4],
                photo(f, 6), DeductionClaim.Status.valueOf(f[5]))));
        read(MESSAGES, f -> lease.restore(new Message(f[0], f[1], LocalDateTime.parse(f[2]), f[3])));
        read(REPAIRS, f -> lease.restore(new MaintenanceRequest(f[0], f[1], f[2], MaintenanceRequest.Urgency.valueOf(f[3]),
                photo(f, 6), LocalDateTime.parse(f[5]), MaintenanceRequest.Status.valueOf(f[4]))));
        read(DISPUTES, f -> lease.restore(new Dispute(f[0], f[1], Dispute.Topic.valueOf(f[2]), f[3], LocalDateTime.parse(f[4]),
                f[5], text(f[6]), text(f[7]), text(f[8]), decimal(f[9]), bool(f[10]), bool(f[11]), Dispute.Status.valueOf(f[12]))));
        read(CALL_LOG, f -> lease.restore(new CallLogEntry(f[0], LocalDateTime.parse(f[1]), f[2], f[3])));
    }

    private void read(String fileName, Consumer<String[]> parser) {
        Path file = folder.resolve(fileName);
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String where = fileName + " line " + lineNumber + " skipped: ";
                try {
                    parser.accept(RecordCodec.split(line));
                } catch (NumberFormatException e) {
                    warnings.add(where + "bad number (" + e.getMessage() + ")");
                } catch (DateTimeParseException e) {
                    warnings.add(where + "bad date '" + e.getParsedString() + "'");
                } catch (IllegalArgumentException e) {
                    warnings.add(where + e.getMessage());
                } catch (ArrayIndexOutOfBoundsException e) {
                    warnings.add(where + "missing fields");
                }
            }
        } catch (FileNotFoundException e) {
            // nothing saved yet: normal on the first start
        } catch (IOException e) {
            warnings.add("Could not read " + file + ": " + e.getMessage());
        }
    }

    private static User parseUser(String[] f) {
        Role role = Role.valueOf(f[1]);
        return switch (role) {
            case TENANT -> new Tenant(f[0], f[2], f[3], f[4], f[5]);
            case LANDLORD -> new Landlord(f[0], f[2], f[3], f[4], f[5], f[6]);
            case AGENT -> new CallCenterAgent(f[0], f[2], f[3], f[4], f[5]);
        };
    }

    private static Set<BillType> parseBills(String raw) {
        Set<BillType> bills = EnumSet.noneOf(BillType.class);
        if (!raw.isEmpty()) {
            for (String name : raw.split(",")) {
                bills.add(BillType.valueOf(name));
            }
        }
        return bills;
    }

    private static Photo photo(String[] f, int from) {
        if (f[from].isEmpty()) {
            return null;
        }
        return new Photo(f[from], f[from + 1], LocalDateTime.parse(f[from + 2]));
    }

    private static String text(String raw) {
        return raw.isEmpty() ? null : raw;
    }

    private static LocalDate date(String raw) {
        return raw.isEmpty() ? null : LocalDate.parse(raw);
    }

    private static LocalDateTime dateTime(String raw) {
        return raw.isEmpty() ? null : LocalDateTime.parse(raw);
    }

    private static Double decimal(String raw) {
        return raw.isEmpty() ? null : Double.valueOf(raw);
    }

    private static Boolean bool(String raw) {
        return raw.isEmpty() ? null : Boolean.valueOf(raw);
    }

    // ---------------------------------------------------------------- save

    public void save(FairLease lease) throws IOException {
        Files.createDirectories(folder);

        List<String> users = new ArrayList<>();
        for (User u : lease.getUsers()) {
            String iban = u instanceof Landlord landlord ? landlord.getIban() : null;
            users.add(RecordCodec.join(u.getId(), u.getRole().name(), u.getName(), u.getEmail(), u.getPhone(), u.getPasswordHash(), iban));
        }
        write(USERS, "id|role|name|email|phone|passwordHash|iban", users);

        List<String> properties = new ArrayList<>();
        List<String> items = new ArrayList<>();
        for (Property p : lease.getProperties()) {
            properties.add(RecordCodec.join(p.getId(), p.getLandlordId(), p.getType().name(), p.getAddress(), p.getSurface(), p.getStatus().name()));
            for (Item i : p.getItems()) {
                items.add(RecordCodec.join(i.getId(), i.getPropertyId(), i.getRoom(), i.getName(), i.getCategory().name()));
            }
        }
        write(PROPERTIES, "id|landlordId|type|address|surface|status", properties);
        write(ITEMS, "id|propertyId|room|name|category", items);

        List<String> agreements = new ArrayList<>();
        for (Agreement a : lease.getAgreements()) {
            List<String> bills = new ArrayList<>();
            a.getIncludedBills().forEach(b -> bills.add(b.name()));
            agreements.add(RecordCodec.join(a.getId(), a.getPropertyId(), a.getLandlordId(), a.getTenantId(), a.getStartDate(),
                    a.getEndDate(), a.getMonthlyRent(), a.getDueDay(), a.getDepositAmount(), String.join(",", bills),
                    a.getHouseRules(), a.getStatus().name(), a.getSignedAt()));
        }
        write(AGREEMENTS, "id|propertyId|landlordId|tenantId|start|end|rent|dueDay|deposit|includedBills|rules|status|signedAt", agreements);

        List<String> inspections = new ArrayList<>();
        List<String> entries = new ArrayList<>();
        for (Inspection i : lease.getInspections()) {
            inspections.add(RecordCodec.join(i.getId(), i.getAgreementId(), i.getType().name(), i.getSubmittedAt(),
                    i.isLandlordConfirmed(), i.getLandlordComment()));
            for (InspectionEntry e : i.getEntries()) {
                entries.add(RecordCodec.join(concat(new Object[]{i.getId(), e.getItemId(), e.getCondition().name(), e.getNote(),
                        e.getMeterReading()}, photoFields(e.getPhoto()))));
            }
        }
        write(INSPECTIONS, "id|agreementId|type|submittedAt|landlordConfirmed|landlordComment", inspections);
        write(ENTRIES, "inspectionId|itemId|condition|note|meterReading|photoFile|photoSha256|photoTakenAt", entries);

        List<String> invoices = new ArrayList<>();
        for (Invoice i : lease.getInvoices()) {
            invoices.add(RecordCodec.join(concat(new Object[]{i.getId(), i.getAgreementId(), i.getType().name(), i.getPeriod(),
                    i.getAmount(), i.getStatus().name(), i.getContestReason(), i.getPaidOn()}, photoFields(i.getPhoto()))));
        }
        write(INVOICES, "id|agreementId|type|period|amount|status|contestReason|paidOn|photoFile|photoSha256|photoTakenAt", invoices);

        List<String> payments = new ArrayList<>();
        for (Payment p : lease.getPayments()) {
            payments.add(RecordCodec.join(p.getId(), p.getAgreementId(), p.getDate(), p.getAmount(), p.getMethod().name(),
                    String.join(",", p.getPaidIds())));
        }
        write(PAYMENTS, "id|agreementId|date|amount|method|paidIds", payments);

        List<String> deposits = new ArrayList<>();
        List<String> claims = new ArrayList<>();
        for (Deposit d : lease.getDeposits()) {
            deposits.add(RecordCodec.join(d.getAgreementId(), d.getAmount(), d.getStatus().name(), d.getPaidOn(),
                    d.getReturnedAmount(), d.getReturnedOn()));
            for (DeductionClaim c : d.getClaims()) {
                claims.add(RecordCodec.join(concat(new Object[]{c.getId(), c.getAgreementId(), c.getItemId(), c.getAmount(),
                        c.getReason(), c.getStatus().name()}, photoFields(c.getPhoto()))));
            }
        }
        write(DEPOSITS, "agreementId|amount|status|paidOn|returnedAmount|returnedOn", deposits);
        write(CLAIMS, "id|agreementId|itemId|amount|reason|status|photoFile|photoSha256|photoTakenAt", claims);

        List<String> messages = new ArrayList<>();
        for (Message m : lease.getMessages()) {
            messages.add(RecordCodec.join(m.getAgreementId(), m.getSenderId(), m.getSentAt(), m.getText()));
        }
        write(MESSAGES, "agreementId|senderId|sentAt|text", messages);

        List<String> repairs = new ArrayList<>();
        for (MaintenanceRequest r : lease.getRepairs()) {
            repairs.add(RecordCodec.join(concat(new Object[]{r.getId(), r.getAgreementId(), r.getDescription(), r.getUrgency().name(),
                    r.getStatus().name(), r.getCreatedAt()}, photoFields(r.getPhoto()))));
        }
        write(REPAIRS, "id|agreementId|description|urgency|status|createdAt|photoFile|photoSha256|photoTakenAt", repairs);

        List<String> disputes = new ArrayList<>();
        for (Dispute d : lease.getCallCenter().getDisputes()) {
            disputes.add(RecordCodec.join(d.getId(), d.getAgreementId(), d.getTopic().name(), d.getOpenedBy(), d.getOpenedAt(),
                    d.getDescription(), d.getRelatedId(), d.getAgentId(), d.getProposal(), d.getProposedAmount(),
                    d.getTenantAccepts(), d.getLandlordAccepts(), d.getStatus().name()));
        }
        write(DISPUTES, "id|agreementId|topic|openedBy|openedAt|description|relatedId|agentId|proposal|proposedAmount|tenantAccepts|landlordAccepts|status", disputes);

        List<String> log = new ArrayList<>();
        for (CallLogEntry e : lease.getCallCenter().getCallLog()) {
            log.add(RecordCodec.join(e.getDisputeId(), e.getAt(), e.getAgentId(), e.getSummary()));
        }
        write(CALL_LOG, "disputeId|at|agentId|summary", log);
    }

    private void write(String fileName, String header, List<String> lines) throws IOException {
        Path target = folder.resolve(fileName);
        Path temp = folder.resolve(fileName + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writer.write("# " + header);
            writer.newLine();
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Object[] photoFields(Photo photo) {
        return photo == null ? new Object[]{null, null, null}
                : new Object[]{photo.getFileName(), photo.getSha256(), photo.getTakenAt()};
    }

    private static Object[] concat(Object[] first, Object[] second) {
        Object[] all = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    /** Writes a document (receipt, case file...) into the agreement's document folder and returns its path. */
    public Path writeDocument(String agreementId, String fileName, String content) throws IOException {
        Path dir = folder.resolve("documents").resolve(agreementId);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    public boolean hasSavedData() {
        return Files.exists(folder.resolve(USERS));
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public Path getFolder() {
        return folder;
    }
}
