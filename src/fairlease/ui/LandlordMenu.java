package fairlease.ui;

import fairlease.exceptions.AgreementStateException;
import fairlease.exceptions.InvalidInputException;
import fairlease.exceptions.UnfairClaimException;
import fairlease.model.Agreement;
import fairlease.model.BillType;
import fairlease.model.DeductionClaim;
import fairlease.model.Inspection;
import fairlease.model.Invoice;
import fairlease.model.Item;
import fairlease.model.Landlord;
import fairlease.model.MaintenanceRequest;
import fairlease.model.Photo;
import fairlease.model.Property;
import fairlease.model.Tenant;
import fairlease.model.User;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LandlordMenu extends PartyMenu {
    private final Landlord landlord;

    public LandlordMenu(FairLeaseApp app, Landlord landlord) {
        super(app, landlord);
        this.landlord = landlord;
    }

    @Override
    protected String title() {
        return "Landlord - " + landlord.getName();
    }

    @Override
    protected String[] options() {
        return new String[]{
                "My properties",
                "Add a property and its inventory",
                "Create a rental agreement",
                "Review inspection reports",
                "Upload a utility bill",
                "Invoices and payments",
                "Repair requests",
                "Deposit: claim a deduction or return it",
                "Chat with a tenant",
                "Call center"
        };
    }

    @Override
    protected void handle(int choice) throws AgreementStateException, UnfairClaimException {
        switch (choice) {
            case 1 -> properties();
            case 2 -> addProperty();
            case 3 -> createAgreement();
            case 4 -> reviewInspection();
            case 5 -> uploadBill();
            case 6 -> history();
            case 7 -> repairs();
            case 8 -> deposit();
            case 9 -> chat();
            case 10 -> callCenter();
            default -> { }
        }
    }

    private void properties() {
        List<Property> mine = lease.propertiesOf(landlord);
        if (mine.isEmpty()) {
            out.println("  You have no properties yet.");
        }
        for (Property p : mine) {
            views.property(p);
        }
    }

    private void addProperty() throws AgreementStateException {
        Property.Type type = in.choice("Type of property:", Property.Type.values());
        String address = in.text("Address: ", "Address", 5, 120);
        double surface = in.ask("Surface in m2: ", raw -> InputRules.decimal(raw, "Surface", 5, 2000, 1));
        Property property = lease.addProperty(landlord, type, address, surface);
        out.println("  Water, electricity and gas meters were added automatically.");
        out.println("Now add the rooms and everything inside them. The tenant will photograph each item at move-in.");
        while (true) {
            String room = in.askOptional("Room name (e.g. Kitchen, Bedroom 1) or Enter to finish: ", raw -> {
                String value = InputRules.text(raw, "Room", 2, 40);
                if (value.equalsIgnoreCase(Property.METER_ROOM)) {
                    throw new InvalidInputException("'" + Property.METER_ROOM + "' is reserved for the utility meters.");
                }
                return value;
            });
            if (room == null) {
                break;
            }
            while (true) {
                String name = in.askOptional("  Item in " + room + " (Enter when the room is done): ", raw -> {
                    String value = InputRules.text(raw, "Item", 2, 60);
                    boolean exists = property.getItemsIn(room).stream().anyMatch(i -> i.getName().equalsIgnoreCase(value));
                    if (exists) {
                        throw new InvalidInputException(room + " already has an item called " + value + ".");
                    }
                    return value;
                });
                if (name == null) {
                    break;
                }
                Item.Category category = in.choice("  Category:", new Item.Category[]{
                        Item.Category.FURNITURE, Item.Category.APPLIANCE, Item.Category.FIXTURE});
                lease.addItem(property, room, name, category);
            }
        }
        out.println();
        views.property(property);
    }

    private void createAgreement() throws AgreementStateException {
        List<Property> free = new ArrayList<>();
        for (Property p : lease.propertiesOf(landlord)) {
            if (p.getStatus() == Property.Status.FREE) {
                free.add(p);
            }
        }
        Property property = in.pick("Which property", free, Property::toString);
        if (property == null) {
            return;
        }
        Tenant tenant = askTenant();
        if (tenant == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = in.ask("Start date (YYYY-MM-DD): ", raw -> {
            LocalDate date = InputRules.date(raw, "Start date");
            if (date.isBefore(today.minusMonths(6)) || date.isAfter(today.plusYears(1))) {
                throw new InvalidInputException("The start date must be between " + today.minusMonths(6) + " and " + today.plusYears(1) + ".");
            }
            return date;
        });
        int months = in.number("Duration in months (1-60): ", "Duration", 1, 60);
        double rent = in.money("Monthly rent: ", "Rent", 50, 100_000);
        int dueDay = in.number("Rent is due on day (1-28) of each month: ", "Due day", 1, 28);
        double deposit = in.money("Security deposit (0 to 6 x rent): ", "Deposit", 0, rent * 6);
        Set<BillType> included = EnumSet.noneOf(BillType.class);
        for (BillType bill : BillType.values()) {
            if (bill.isUtility() && in.yesNo("Is " + bill + " included in the rent?")) {
                included.add(bill);
            }
        }
        String rules = in.askOptional("House rules (Enter for none): ", raw -> InputRules.text(raw, "House rules", 3, 300));
        Agreement agreement = lease.createAgreement(landlord, property, tenant, start, start.plusMonths(months).minusDays(1),
                rent, dueDay, deposit, included, rules);
        out.println();
        views.agreement(agreement);
        out.println("  Sent to " + tenant.getName() + ", who must sign it in FairLease.");
    }

    private Tenant askTenant() {
        while (true) {
            String email = in.askOptional("Tenant's e-mail (they need a FairLease account; Enter to cancel): ", InputRules::email);
            if (email == null) {
                return null;
            }
            Optional<User> user = lease.findByEmail(email);
            if (user.isPresent() && user.get() instanceof Tenant tenant) {
                return tenant;
            }
            out.println("  ! No tenant account uses " + email + ". Ask the tenant to register first.");
        }
    }

    private void reviewInspection() throws AgreementStateException {
        Inspection inspection = in.pick("Reports waiting for you", lease.inspectionsToReview(landlord),
                i -> i.getId() + "  " + i.getType() + "  agreement " + i.getAgreementId() + "  submitted " + Views.stamp(i.getSubmittedAt()));
        if (inspection == null) {
            return;
        }
        Agreement agreement = lease.agreement(inspection.getAgreementId()).orElseThrow();
        views.inspection(inspection);
        if (inspection.getType() == Inspection.Type.MOVE_OUT) {
            out.println();
            views.comparison(agreement);
        }
        int choice = in.menu("Your answer", "Decide later", "Confirm the report", "Send it back to the tenant with a comment");
        if (choice == 1) {
            lease.confirmInspection(landlord, inspection);
            out.println("  Confirmed. The report is locked and kept as evidence.");
            if (inspection.getType() == Inspection.Type.MOVE_OUT) {
                out.println("  The rental has ended. You have " + app.getConfig().getDepositReturnDays()
                        + " day(s) to claim deductions; after that the deposit is returned automatically.");
            }
        } else if (choice == 2) {
            String comment = in.text("Comment for the tenant: ", "Comment", 5, 300);
            lease.sendInspectionBack(landlord, inspection, comment);
            out.println("  Sent back. The tenant can re-photograph the items and submit again.");
        }
    }

    private void uploadBill() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Bill for which rental", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        List<BillType> billable = new ArrayList<>();
        for (BillType type : BillType.values()) {
            if (type.isUtility() && !agreement.includes(type)) {
                billable.add(type);
            }
        }
        BillType type = in.pick("Type of bill", billable, BillType::name);
        if (type == null) {
            return;
        }
        YearMonth period = in.ask("Billing month (YYYY-MM): ", raw -> {
            YearMonth month = InputRules.month(raw, "Billing month");
            if (!agreement.covers(month)) {
                throw new InvalidInputException("The rental runs from " + YearMonth.from(agreement.getStartDate())
                        + " to " + YearMonth.from(agreement.getEndDate()) + ".");
            }
            return month;
        });
        double amount = in.money("Amount: ", "Amount", 0.01, 100_000);
        Photo photo = in.photo("Path to a photo of the original bill", vault, true);
        Invoice bill = lease.uploadBill(landlord, agreement, type, period, amount, photo);
        out.println("  Bill " + bill.getId() + " added to the tenant's monthly statement.");
    }

    private void history() {
        Agreement agreement = chooseAgreement("Which rental", PartyMenu::isRunningOrEnded);
        if (agreement != null) {
            views.history(agreement);
        }
    }

    private void repairs() throws AgreementStateException {
        Agreement agreement = chooseAgreement("Repairs of which rental", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        List<MaintenanceRequest> open = new ArrayList<>();
        for (MaintenanceRequest r : lease.repairsOf(agreement)) {
            if (r.getStatus() != MaintenanceRequest.Status.SOLVED) {
                open.add(r);
            }
        }
        MaintenanceRequest request = in.pick("Open repair requests", open, views::repairLine);
        if (request == null) {
            return;
        }
        MaintenanceRequest.Status[] all = MaintenanceRequest.Status.values();
        MaintenanceRequest.Status next = in.choice("New status:",
                Arrays.copyOfRange(all, request.getStatus().ordinal() + 1, all.length));
        lease.updateRepair(landlord, request, next);
        out.println("  Updated. The tenant sees the new status.");
    }

    private void deposit() throws AgreementStateException, UnfairClaimException {
        Agreement agreement = chooseAgreement("Deposit of which rental", PartyMenu::isRunningOrEnded);
        if (agreement == null) {
            return;
        }
        views.deposit(agreement);
        out.println();
        views.comparison(agreement);
        int choice = in.menu("Deposit", "Back", "Claim a deduction for a damaged item", "Return the deposit now");
        if (choice == 1) {
            List<Item> items = new ArrayList<>();
            for (Item item : lease.propertyOf(agreement).getItems()) {
                if (!item.isMeter()) {
                    items.add(item);
                }
            }
            Item item = in.pick("Which item was damaged", items, Item::toString);
            if (item == null) {
                return;
            }
            double amount = in.money("Repair or replacement cost: ", "Amount", 1, agreement.getDepositAmount());
            String reason = in.text("Reason (e.g. repair invoice no. 123 for the broken leg): ", "Reason", 5, 200);
            DeductionClaim claim = lease.fileClaim(landlord, agreement, item, amount, reason, LocalDate.now());
            out.println("  Claim " + claim.getId() + " sent to the tenant with the move-out photo as evidence.");
        } else if (choice == 2) {
            double returned = lease.returnDeposit(agreement, LocalDate.now());
            out.println("  " + views.money(returned) + " returned to " + lease.nameOf(agreement.getTenantId()) + ".");
        }
    }
}
