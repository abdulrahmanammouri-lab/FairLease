package fairlease.service;

import fairlease.config.AppConfig;
import fairlease.exceptions.AgreementStateException;
import fairlease.exceptions.InvalidInputException;
import fairlease.exceptions.UnfairClaimException;
import fairlease.model.Agreement;
import fairlease.model.BillType;
import fairlease.model.CallCenterAgent;
import fairlease.model.CallLogEntry;
import fairlease.model.DeductionClaim;
import fairlease.model.Deposit;
import fairlease.model.Disputable;
import fairlease.model.Dispute;
import fairlease.model.Inspection;
import fairlease.model.InspectionEntry;
import fairlease.model.Invoice;
import fairlease.model.Item;
import fairlease.model.Landlord;
import fairlease.model.MaintenanceRequest;
import fairlease.model.Message;
import fairlease.model.Payable;
import fairlease.model.Payment;
import fairlease.model.Photo;
import fairlease.model.Property;
import fairlease.model.Tenant;
import fairlease.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The FairLease platform: every user, property and rental, and the rules that keep both sides protected.
 */
public class FairLease {
    private final AppConfig config;
    private final CallCenter callCenter;

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Property> properties = new LinkedHashMap<>();
    private final Map<String, Agreement> agreements = new LinkedHashMap<>();
    private final Map<String, Inspection> inspections = new LinkedHashMap<>();
    private final Map<String, Invoice> invoices = new LinkedHashMap<>();
    private final Map<String, Deposit> depositsByAgreement = new LinkedHashMap<>();
    private final List<Payment> payments = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();
    private final List<MaintenanceRequest> repairs = new ArrayList<>();
    private final Map<String, Integer> lastIdByPrefix = new HashMap<>();

    public FairLease(AppConfig config) {
        this.config = config;
        this.callCenter = new CallCenter(config.getCallCenterOpens(), config.getCallCenterCloses());
    }

    // ---------------------------------------------------------------- ids

    public String newId(String prefix) {
        return prefix + lastIdByPrefix.merge(prefix, 1, Integer::sum);
    }

    private void noteId(String id) {
        int split = 0;
        while (split < id.length() && Character.isLetter(id.charAt(split))) {
            split++;
        }
        if (split == 0 || split == id.length()) {
            return;
        }
        try {
            lastIdByPrefix.merge(id.substring(0, split), Integer.parseInt(id.substring(split)), Math::max);
        } catch (NumberFormatException e) {
            // ids without a numeric suffix can never collide with generated ones
        }
    }

    // ---------------------------------------------------------------- accounts

    public Tenant registerTenant(String name, String email, String phone, String password) throws InvalidInputException {
        requireFreeEmail(email);
        Tenant tenant = new Tenant(newId("U"), name, email, phone, User.hashPassword(email, password));
        users.put(tenant.getId(), tenant);
        return tenant;
    }

    public Landlord registerLandlord(String name, String email, String phone, String password, String iban)
            throws InvalidInputException {
        requireFreeEmail(email);
        Landlord landlord = new Landlord(newId("U"), name, email, phone, User.hashPassword(email, password), iban);
        users.put(landlord.getId(), landlord);
        return landlord;
    }

    public CallCenterAgent registerAgent(String name, String email, String phone, String password, String code)
            throws InvalidInputException {
        if (!config.getAgentCode().equals(code)) {
            throw new InvalidInputException("Wrong agent registration code.");
        }
        requireFreeEmail(email);
        CallCenterAgent agent = new CallCenterAgent(newId("U"), name, email, phone, User.hashPassword(email, password));
        users.put(agent.getId(), agent);
        return agent;
    }

    private void requireFreeEmail(String email) throws InvalidInputException {
        if (findByEmail(email).isPresent()) {
            throw new InvalidInputException("An account with " + email + " already exists.");
        }
    }

    public Optional<User> findByEmail(String email) {
        return users.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
    }

    public Optional<User> login(String email, String password) {
        return findByEmail(email).filter(u -> u.checkPassword(password));
    }

    public String nameOf(String userId) {
        User user = users.get(userId);
        return user == null ? "unknown user " + userId : user.getName();
    }

    // ---------------------------------------------------------------- properties

    public Property addProperty(Landlord owner, Property.Type type, String address, double surface) {
        Property property = new Property(newId("P"), owner.getId(), type, address, surface, Property.Status.FREE);
        for (String meter : List.of("Water meter", "Electricity meter", "Gas meter")) {
            property.addItem(new Item(newId("I"), property.getId(), Property.METER_ROOM, meter, Item.Category.METER));
        }
        properties.put(property.getId(), property);
        return property;
    }

    public Item addItem(Property property, String room, String name, Item.Category category) throws AgreementStateException {
        if (property.getStatus() != Property.Status.FREE) {
            throw new AgreementStateException("The inventory can only change while the property is free.");
        }
        Item item = new Item(newId("I"), property.getId(), room, name, category);
        property.addItem(item);
        return item;
    }

    public List<Property> propertiesOf(Landlord landlord) {
        List<Property> result = new ArrayList<>();
        for (Property p : properties.values()) {
            if (p.getLandlordId().equals(landlord.getId())) {
                result.add(p);
            }
        }
        return result;
    }

    public Property propertyOf(Agreement agreement) {
        return properties.get(agreement.getPropertyId());
    }

    // ---------------------------------------------------------------- agreements

    public Agreement createAgreement(Landlord landlord, Property property, Tenant tenant, LocalDate start, LocalDate end,
                                     double rent, int dueDay, double deposit, Set<BillType> includedBills, String rules)
            throws AgreementStateException {
        if (!property.getLandlordId().equals(landlord.getId())) {
            throw new AgreementStateException("You can only rent out your own properties.");
        }
        if (property.getStatus() != Property.Status.FREE) {
            throw new AgreementStateException("The property is " + property.getStatus() + ".");
        }
        boolean pendingExists = agreements.values().stream().anyMatch(a ->
                a.getPropertyId().equals(property.getId()) && a.getStatus() == Agreement.Status.PENDING);
        if (pendingExists) {
            throw new AgreementStateException("This property already has an agreement waiting for a tenant's answer.");
        }
        if (!end.isAfter(start)) {
            throw new AgreementStateException("The agreement must end after it starts.");
        }
        Agreement agreement = new Agreement(newId("A"), property.getId(), landlord.getId(), tenant.getId(), start, end,
                rent, dueDay, deposit, includedBills, rules, Agreement.Status.PENDING, null);
        agreements.put(agreement.getId(), agreement);
        depositsByAgreement.put(agreement.getId(),
                new Deposit(agreement.getId(), deposit, Deposit.Status.NOT_PAID, null, 0, null));
        return agreement;
    }

    public List<Agreement> agreementsOf(User user) {
        List<Agreement> result = new ArrayList<>();
        for (Agreement a : agreements.values()) {
            if (a.isParty(user)) {
                result.add(a);
            }
        }
        return result;
    }

    public void signAgreement(Tenant tenant, Agreement agreement) throws AgreementStateException {
        requireParty(tenant, agreement);
        agreement.sign(LocalDateTime.now());
        refreshPropertyStatus(propertyOf(agreement));
    }

    public void rejectAgreement(Tenant tenant, Agreement agreement) throws AgreementStateException {
        requireParty(tenant, agreement);
        agreement.reject();
    }

    private void requireParty(User user, Agreement agreement) throws AgreementStateException {
        if (!agreement.isParty(user)) {
            throw new AgreementStateException("You are not part of agreement " + agreement.getId() + ".");
        }
    }

    private void refreshPropertyStatus(Property property) {
        boolean inDispute = false;
        boolean rented = false;
        for (Agreement a : agreements.values()) {
            if (!a.getPropertyId().equals(property.getId())) {
                continue;
            }
            rented |= a.getStatus() == Agreement.Status.ACTIVE;
            inDispute |= callCenter.disputesFor(a.getId()).stream().anyMatch(Dispute::isOpen);
        }
        property.setStatus(inDispute ? Property.Status.IN_DISPUTE : rented ? Property.Status.RENTED : Property.Status.FREE);
    }

    // ---------------------------------------------------------------- inspections

    public Optional<Inspection> inspectionOf(Agreement agreement, Inspection.Type type) {
        return inspections.values().stream()
                .filter(i -> i.getAgreementId().equals(agreement.getId()) && i.getType() == type)
                .findFirst();
    }

    public Inspection startInspection(Tenant tenant, Agreement agreement, Inspection.Type type) throws AgreementStateException {
        requireParty(tenant, agreement);
        if (agreement.getStatus() != Agreement.Status.ACTIVE) {
            throw new AgreementStateException("Inspections are only possible on a signed, active agreement.");
        }
        if (type == Inspection.Type.MOVE_OUT && inspectionOf(agreement, Inspection.Type.MOVE_IN).filter(Inspection::isFinal).isEmpty()) {
            throw new AgreementStateException("The move-in report must be confirmed before the move-out inspection.");
        }
        Optional<Inspection> existing = inspectionOf(agreement, type);
        if (existing.isPresent()) {
            if (existing.get().isSubmitted()) {
                throw new AgreementStateException("The " + type + " report was already submitted.");
            }
            return existing.get();
        }
        Inspection inspection = new Inspection(newId("IN"), agreement.getId(), type, null, false, null);
        inspections.put(inspection.getId(), inspection);
        return inspection;
    }

    public void submitInspection(Inspection inspection) throws AgreementStateException {
        Agreement agreement = agreements.get(inspection.getAgreementId());
        inspection.submit(LocalDateTime.now(), propertyOf(agreement).getItems());
    }

    public List<Inspection> inspectionsToReview(Landlord landlord) {
        List<Inspection> result = new ArrayList<>();
        for (Inspection i : inspections.values()) {
            Agreement a = agreements.get(i.getAgreementId());
            if (a.getLandlordId().equals(landlord.getId()) && i.isSubmitted() && !i.isFinal()) {
                result.add(i);
            }
        }
        return result;
    }

    public void confirmInspection(Landlord landlord, Inspection inspection) throws AgreementStateException {
        Agreement agreement = agreements.get(inspection.getAgreementId());
        requireParty(landlord, agreement);
        inspection.confirmByLandlord();
        if (inspection.getType() == Inspection.Type.MOVE_OUT) {
            endRental(agreement);
        }
    }

    public void sendInspectionBack(Landlord landlord, Inspection inspection, String comment) throws AgreementStateException {
        requireParty(landlord, agreements.get(inspection.getAgreementId()));
        inspection.sendBack(comment);
    }

    private void endRental(Agreement agreement) {
        agreement.end();
        refreshPropertyStatus(propertyOf(agreement));
    }

    // ---------------------------------------------------------------- rules that run on their own

    /** Applies every deadline-based rule and returns a line for each thing that changed. */
    public List<String> applyAutomaticRules(LocalDateTime now) {
        List<String> notices = new ArrayList<>();
        for (Inspection inspection : inspections.values()) {
            if (inspection.autoAcceptIfExpired(now, config.getInspectionResponseHours())) {
                Agreement agreement = agreements.get(inspection.getAgreementId());
                notices.add(inspection.getType() + " report of agreement " + agreement.getId() + " accepted automatically: the landlord did not answer within "
                        + config.getInspectionResponseHours() + " hours.");
                if (inspection.getType() == Inspection.Type.MOVE_OUT) {
                    endRental(agreement);
                }
            }
        }
        for (Agreement agreement : agreements.values()) {
            if (agreement.getStatus() == Agreement.Status.ACTIVE) {
                generateRentInvoices(agreement, YearMonth.from(now));
            }
            Deposit deposit = depositOf(agreement);
            if (agreement.getStatus() == Agreement.Status.ENDED && claimDeadlinePassed(agreement, now.toLocalDate())
                    && (deposit.getStatus() == Deposit.Status.HELD || deposit.getStatus() == Deposit.Status.UNDER_REVIEW)) {
                try {
                    double back = returnDeposit(agreement, now.toLocalDate());
                    notices.add("Deposit of agreement " + agreement.getId() + " returned automatically: " + back + " " + config.getCurrency() + ".");
                } catch (AgreementStateException e) {
                    // stays held until claims or unpaid bills are settled
                }
            }
        }
        for (MaintenanceRequest repair : repairs) {
            if (repair.needsEscalation(now, config.getRepairEscalationHours())
                    && callCenter.openDisputeAbout(repair.getId()).isEmpty()) {
                Agreement agreement = agreements.get(repair.getAgreementId());
                Dispute dispute = new Dispute(newId("D"), agreement.getId(), Dispute.Topic.REPAIR, agreement.getTenantId(), now,
                        "Urgent repair not answered: " + repair.getDescription(), repair.getId(),
                        null, null, null, null, null, Dispute.Status.WAITING);
                callCenter.file(dispute);
                refreshPropertyStatus(propertyOf(agreement));
                notices.add("Urgent repair " + repair.getId() + " escalated to the call center as case " + dispute.getId() + ".");
            }
        }
        return notices;
    }

    // ---------------------------------------------------------------- rent and bills

    public void generateRentInvoices(Agreement agreement, YearMonth upTo) {
        YearMonth last = YearMonth.from(agreement.getEndDate());
        if (upTo.isBefore(last)) {
            last = upTo;
        }
        for (YearMonth month = YearMonth.from(agreement.getStartDate()); !month.isAfter(last); month = month.plusMonths(1)) {
            if (findInvoice(agreement, BillType.RENT, month).isEmpty()) {
                Invoice rent = new Invoice(newId("INV"), agreement.getId(), BillType.RENT, month, agreement.getMonthlyRent(),
                        null, Invoice.Status.UNPAID, null, null);
                invoices.put(rent.getId(), rent);
            }
        }
    }

    private Optional<Invoice> findInvoice(Agreement agreement, BillType type, YearMonth period) {
        return invoices.values().stream()
                .filter(i -> i.getAgreementId().equals(agreement.getId()) && i.getType() == type && i.getPeriod().equals(period))
                .findFirst();
    }

    public Invoice uploadBill(Landlord landlord, Agreement agreement, BillType type, YearMonth period, double amount, Photo billPhoto)
            throws AgreementStateException {
        requireParty(landlord, agreement);
        boolean depositSettled = depositOf(agreement).getStatus() == Deposit.Status.RETURNED;
        if (agreement.getStatus() != Agreement.Status.ACTIVE && (agreement.getStatus() != Agreement.Status.ENDED || depositSettled)) {
            throw new AgreementStateException("Bills can be uploaded only during the rental or before the deposit is returned.");
        }
        if (!type.isUtility()) {
            throw new AgreementStateException("Rent invoices are generated automatically from the agreement.");
        }
        if (agreement.includes(type)) {
            throw new AgreementStateException(type + " is included in the rent of this agreement and cannot be billed separately.");
        }
        if (!agreement.covers(period)) {
            throw new AgreementStateException(period + " is outside the rental period " + agreement.getStartDate() + " - " + agreement.getEndDate() + ".");
        }
        if (findInvoice(agreement, type, period).isPresent()) {
            throw new AgreementStateException("A " + type + " bill for " + period + " was already uploaded.");
        }
        if (billPhoto == null) {
            throw new AgreementStateException("A photo of the original bill is required.");
        }
        Invoice bill = new Invoice(newId("INV"), agreement.getId(), type, period, amount, billPhoto, Invoice.Status.UNPAID, null, null);
        invoices.put(bill.getId(), bill);
        return bill;
    }

    public List<Invoice> invoicesOf(Agreement agreement) {
        List<Invoice> result = new ArrayList<>();
        for (Invoice i : invoices.values()) {
            if (i.getAgreementId().equals(agreement.getId())) {
                result.add(i);
            }
        }
        result.sort(Comparator.comparing(Invoice::getPeriod).thenComparing(Invoice::getType));
        return result;
    }

    public List<Invoice> openInvoices(Agreement agreement) {
        List<Invoice> result = new ArrayList<>();
        for (Invoice i : invoicesOf(agreement)) {
            if (i.getStatus() != Invoice.Status.PAID) {
                result.add(i);
            }
        }
        return result;
    }

    public double unpaidTotal(Agreement agreement) {
        return openInvoices(agreement).stream().filter(Invoice::isPayable).mapToDouble(Invoice::getAmountDue).sum();
    }

    /** One payment for the whole monthly statement: rent and every bill that is not on hold. */
    public Payment payStatement(Tenant tenant, Agreement agreement, Payment.Method method, LocalDate today) throws AgreementStateException {
        requireParty(tenant, agreement);
        List<Payable> due = new ArrayList<>();
        for (Invoice invoice : openInvoices(agreement)) {
            if (invoice.isPayable()) {
                due.add(invoice);
            }
        }
        return pay(agreement, due, method, today);
    }

    public Payment payDeposit(Tenant tenant, Agreement agreement, Payment.Method method, LocalDate today) throws AgreementStateException {
        requireParty(tenant, agreement);
        if (agreement.getStatus() != Agreement.Status.ACTIVE) {
            throw new AgreementStateException("The deposit is paid after the agreement is signed.");
        }
        return pay(agreement, List.of(depositOf(agreement)), method, today);
    }

    private Payment pay(Agreement agreement, Collection<? extends Payable> items, Payment.Method method, LocalDate today)
            throws AgreementStateException {
        List<String> ids = new ArrayList<>();
        double total = 0;
        for (Payable item : items) {
            if (item.isPayable()) {
                total += item.getAmountDue();
                ids.add(item.getId());
            }
        }
        if (ids.isEmpty()) {
            throw new AgreementStateException("There is nothing to pay right now.");
        }
        for (Payable item : items) {
            if (item.isPayable()) {
                item.markPaid(today);
            }
        }
        Payment payment = new Payment(newId("PAY"), agreement.getId(), today, total, method, ids);
        payments.add(payment);
        return payment;
    }

    public List<Payment> paymentsOf(Agreement agreement) {
        List<Payment> result = new ArrayList<>();
        for (Payment p : payments) {
            if (p.getAgreementId().equals(agreement.getId())) {
                result.add(p);
            }
        }
        return result;
    }

    public Dispute contestBill(Tenant tenant, Invoice invoice, String reason) throws AgreementStateException {
        Agreement agreement = agreements.get(invoice.getAgreementId());
        requireParty(tenant, agreement);
        invoice.contest(reason);
        return openDispute(tenant, agreement, Dispute.Topic.BILL, "Contested " + invoice.describe() + ": " + reason, invoice.getId());
    }

    // ---------------------------------------------------------------- deposit

    public Deposit depositOf(Agreement agreement) {
        return depositsByAgreement.get(agreement.getId());
    }

    private boolean claimDeadlinePassed(Agreement agreement, LocalDate today) {
        return inspectionOf(agreement, Inspection.Type.MOVE_OUT)
                .filter(Inspection::isFinal)
                .map(i -> i.getSubmittedAt().toLocalDate().plusDays(config.getDepositReturnDays()).isBefore(today))
                .orElse(false);
    }

    public DeductionClaim fileClaim(Landlord landlord, Agreement agreement, Item item, double amount, String reason, LocalDate today)
            throws AgreementStateException, UnfairClaimException {
        requireParty(landlord, agreement);
        Inspection moveOut = inspectionOf(agreement, Inspection.Type.MOVE_OUT).filter(Inspection::isFinal)
                .orElseThrow(() -> new UnfairClaimException("A deduction needs the confirmed move-out report as evidence."));
        Inspection moveIn = inspectionOf(agreement, Inspection.Type.MOVE_IN)
                .orElseThrow(() -> new UnfairClaimException("There is no move-in report to compare with."));
        if (claimDeadlinePassed(agreement, today)) {
            throw new UnfairClaimException("The " + config.getDepositReturnDays() + "-day period for deduction claims has passed.");
        }
        InspectionEntry after = moveOut.entryFor(item.getId())
                .orElseThrow(() -> new UnfairClaimException(item + " is not in the move-out report."));
        InspectionEntry before = moveIn.entryFor(item.getId())
                .orElseThrow(() -> new UnfairClaimException(item + " is not in the move-in report."));
        if (!after.getCondition().isWorseThan(before.getCondition())) {
            throw new UnfairClaimException(item + " was already " + before.getCondition() + " at move-in and is "
                    + after.getCondition() + " at move-out: this cannot be charged to the tenant.");
        }
        if (!after.hasPhoto()) {
            throw new UnfairClaimException("The move-out report has no photo of " + item + "; claims without evidence are not accepted.");
        }
        DeductionClaim claim = new DeductionClaim(newId("CL"), agreement.getId(), item.getId(), amount, reason,
                after.getPhoto(), DeductionClaim.Status.PENDING);
        depositOf(agreement).addClaim(claim);
        return claim;
    }

    public void acceptClaim(Tenant tenant, DeductionClaim claim) throws AgreementStateException {
        requireParty(tenant, agreements.get(claim.getAgreementId()));
        claim.accept();
    }

    public Dispute disputeClaim(Tenant tenant, DeductionClaim claim, String reason) throws AgreementStateException {
        Agreement agreement = agreements.get(claim.getAgreementId());
        requireParty(tenant, agreement);
        claim.contest(reason);
        String description = String.format("Disputed deduction of %.2f %s: %s", claim.getAmount(), config.getCurrency(), reason);
        return openDispute(tenant, agreement, Dispute.Topic.DEPOSIT, description, claim.getId());
    }

    /** Returns what is left of the deposit after accepted claims and unpaid bills; unpaid bills are paid from it. */
    public double returnDeposit(Agreement agreement, LocalDate today) throws AgreementStateException {
        if (agreement.getStatus() != Agreement.Status.ENDED) {
            throw new AgreementStateException("The deposit is returned after the move-out report is confirmed.");
        }
        List<Invoice> open = openInvoices(agreement);
        if (open.stream().anyMatch(Invoice::isContested)) {
            throw new AgreementStateException("Contested bills must be settled before the deposit is returned.");
        }
        double unpaid = unpaidTotal(agreement);
        double returned = depositOf(agreement).settle(unpaid, today);
        if (!open.isEmpty()) {
            pay(agreement, open, Payment.Method.FROM_DEPOSIT, today);
        }
        return returned;
    }

    // ---------------------------------------------------------------- repairs

    public MaintenanceRequest reportRepair(Tenant tenant, Agreement agreement, String description,
                                           MaintenanceRequest.Urgency urgency, Photo photo) throws AgreementStateException {
        requireParty(tenant, agreement);
        if (agreement.getStatus() != Agreement.Status.ACTIVE) {
            throw new AgreementStateException("Repairs can be requested only during an active rental.");
        }
        MaintenanceRequest request = new MaintenanceRequest(newId("R"), agreement.getId(), description, urgency, photo,
                LocalDateTime.now(), MaintenanceRequest.Status.OPEN);
        repairs.add(request);
        return request;
    }

    public List<MaintenanceRequest> repairsOf(Agreement agreement) {
        List<MaintenanceRequest> result = new ArrayList<>();
        for (MaintenanceRequest r : repairs) {
            if (r.getAgreementId().equals(agreement.getId())) {
                result.add(r);
            }
        }
        return result;
    }

    public void updateRepair(Landlord landlord, MaintenanceRequest request, MaintenanceRequest.Status next) throws AgreementStateException {
        requireParty(landlord, agreements.get(request.getAgreementId()));
        request.moveTo(next);
    }

    // ---------------------------------------------------------------- chat

    public Message sendMessage(User sender, Agreement agreement, String text) throws AgreementStateException {
        requireParty(sender, agreement);
        Message message = new Message(agreement.getId(), sender.getId(), LocalDateTime.now(), text);
        messages.add(message);
        return message;
    }

    public List<Message> chatOf(Agreement agreement) {
        List<Message> result = new ArrayList<>();
        for (Message m : messages) {
            if (m.getAgreementId().equals(agreement.getId())) {
                result.add(m);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- call center

    public Dispute openDispute(User opener, Agreement agreement, Dispute.Topic topic, String description, String relatedId)
            throws AgreementStateException {
        requireParty(opener, agreement);
        if (relatedId != null && callCenter.openDisputeAbout(relatedId).isPresent()) {
            throw new AgreementStateException("There is already an open case about " + relatedId + ".");
        }
        Dispute dispute = new Dispute(newId("D"), agreement.getId(), topic, opener.getId(), LocalDateTime.now(), description,
                relatedId, null, null, null, null, null, Dispute.Status.WAITING);
        callCenter.file(dispute);
        refreshPropertyStatus(propertyOf(agreement));
        return dispute;
    }

    public List<Dispute> disputesOf(User party) {
        List<Dispute> result = new ArrayList<>();
        for (Agreement a : agreementsOf(party)) {
            result.addAll(callCenter.disputesFor(a.getId()));
        }
        return result;
    }

    public void takeCase(CallCenterAgent agent, Dispute dispute) throws AgreementStateException {
        dispute.assign(agent.getId());
    }

    public void proposeResolution(CallCenterAgent agent, Dispute dispute, String proposal, Double amount) throws AgreementStateException {
        if (!agent.getId().equals(dispute.getAgentId())) {
            throw new AgreementStateException("Case " + dispute.getId() + " is assigned to another agent.");
        }
        dispute.propose(proposal, amount);
    }

    /** Returns true when the dispute is resolved by this answer; the agreed amount is then applied automatically. */
    public boolean answerProposal(User party, Dispute dispute, boolean accept) throws AgreementStateException {
        Agreement agreement = agreements.get(dispute.getAgreementId());
        requireParty(party, agreement);
        boolean resolved = dispute.answer(party.getRole(), accept);
        if (resolved) {
            if (dispute.getRelatedId() != null && dispute.getProposedAmount() != null) {
                findDisputable(dispute.getRelatedId()).ifPresent(d -> d.applyResolution(dispute.getProposedAmount()));
            }
            refreshPropertyStatus(propertyOf(agreement));
        }
        return resolved;
    }

    public void closeWithoutAgreement(CallCenterAgent agent, Dispute dispute) throws AgreementStateException {
        if (!agent.getId().equals(dispute.getAgentId())) {
            throw new AgreementStateException("Case " + dispute.getId() + " is assigned to another agent.");
        }
        dispute.closeWithoutAgreement();
        refreshPropertyStatus(propertyOf(agreements.get(dispute.getAgreementId())));
    }

    public Optional<Disputable> findDisputable(String id) {
        if (invoices.containsKey(id)) {
            return Optional.of(invoices.get(id));
        }
        for (Deposit deposit : depositsByAgreement.values()) {
            for (DeductionClaim claim : deposit.getClaims()) {
                if (claim.getId().equals(id)) {
                    return Optional.of(claim);
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- loading saved data

    public void restore(User user) {
        users.put(user.getId(), user);
        noteId(user.getId());
    }

    public void restore(Property property) {
        requireKnown(users, property.getLandlordId(), "landlord");
        properties.put(property.getId(), property);
        noteId(property.getId());
    }

    public void restoreItem(Item item) {
        requireKnown(properties, item.getPropertyId(), "property").addItem(item);
        noteId(item.getId());
    }

    public void restore(Agreement agreement) {
        requireKnown(properties, agreement.getPropertyId(), "property");
        requireKnown(users, agreement.getTenantId(), "tenant");
        agreements.put(agreement.getId(), agreement);
        noteId(agreement.getId());
    }

    public void restore(Inspection inspection) {
        requireKnown(agreements, inspection.getAgreementId(), "agreement");
        inspections.put(inspection.getId(), inspection);
        noteId(inspection.getId());
    }

    public void restoreEntry(String inspectionId, InspectionEntry entry) {
        requireKnown(inspections, inspectionId, "inspection").restore(entry);
    }

    public void restore(Invoice invoice) {
        requireKnown(agreements, invoice.getAgreementId(), "agreement");
        invoices.put(invoice.getId(), invoice);
        noteId(invoice.getId());
    }

    public void restore(Payment payment) {
        requireKnown(agreements, payment.getAgreementId(), "agreement");
        payments.add(payment);
        noteId(payment.getId());
    }

    public void restore(Deposit deposit) {
        requireKnown(agreements, deposit.getAgreementId(), "agreement");
        depositsByAgreement.put(deposit.getAgreementId(), deposit);
    }

    public void restoreClaim(DeductionClaim claim) {
        requireKnown(depositsByAgreement, claim.getAgreementId(), "deposit").restoreClaim(claim);
        noteId(claim.getId());
    }

    public void restore(Message message) {
        requireKnown(agreements, message.getAgreementId(), "agreement");
        messages.add(message);
    }

    public void restore(MaintenanceRequest request) {
        requireKnown(agreements, request.getAgreementId(), "agreement");
        repairs.add(request);
        noteId(request.getId());
    }

    public void restore(Dispute dispute) {
        requireKnown(agreements, dispute.getAgreementId(), "agreement");
        callCenter.file(dispute);
        noteId(dispute.getId());
    }

    public void restore(CallLogEntry entry) {
        callCenter.restoreLog(entry);
    }

    private static <T> T requireKnown(Map<String, T> map, String id, String what) {
        T value = map.get(id);
        if (value == null) {
            throw new IllegalArgumentException("unknown " + what + " " + id);
        }
        return value;
    }

    // ---------------------------------------------------------------- read access

    public AppConfig getConfig() {
        return config;
    }

    public CallCenter getCallCenter() {
        return callCenter;
    }

    public Optional<User> user(String id) {
        return Optional.ofNullable(users.get(id));
    }

    public Optional<Agreement> agreement(String id) {
        return Optional.ofNullable(agreements.get(id));
    }

    public Optional<Item> item(Agreement agreement, String itemId) {
        return propertyOf(agreement).findItem(itemId);
    }

    public Collection<User> getUsers() {
        return Collections.unmodifiableCollection(users.values());
    }

    public Collection<Property> getProperties() {
        return Collections.unmodifiableCollection(properties.values());
    }

    public Collection<Agreement> getAgreements() {
        return Collections.unmodifiableCollection(agreements.values());
    }

    public Collection<Inspection> getInspections() {
        return Collections.unmodifiableCollection(inspections.values());
    }

    public Collection<Invoice> getInvoices() {
        return Collections.unmodifiableCollection(invoices.values());
    }

    public Collection<Deposit> getDeposits() {
        return Collections.unmodifiableCollection(depositsByAgreement.values());
    }

    public List<Payment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<MaintenanceRequest> getRepairs() {
        return Collections.unmodifiableList(repairs);
    }
}
