package fairlease.model;

import fairlease.exceptions.AgreementStateException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A move-in or move-out report. The tenant records every item and submits; the report becomes final
 * when the landlord confirms it, or automatically when the landlord does not answer in time.
 */
public class Inspection {
    public enum Type { MOVE_IN, MOVE_OUT }

    private final String id;
    private final String agreementId;
    private final Type type;
    private final Map<String, InspectionEntry> entriesByItem = new LinkedHashMap<>();
    private LocalDateTime submittedAt;
    private boolean landlordConfirmed;
    private String landlordComment;

    public Inspection(String id, String agreementId, Type type, LocalDateTime submittedAt,
                      boolean landlordConfirmed, String landlordComment) {
        this.id = id;
        this.agreementId = agreementId;
        this.type = type;
        this.submittedAt = submittedAt;
        this.landlordConfirmed = landlordConfirmed;
        this.landlordComment = landlordComment;
    }

    public void record(InspectionEntry entry) throws AgreementStateException {
        if (isSubmitted()) {
            throw new AgreementStateException("This report was already submitted and can no longer be changed.");
        }
        entriesByItem.put(entry.getItemId(), entry);
    }

    public void restore(InspectionEntry entry) {
        entriesByItem.put(entry.getItemId(), entry);
    }

    public List<Item> missingItems(Collection<Item> inventory) {
        List<Item> missing = new ArrayList<>();
        for (Item item : inventory) {
            if (!entriesByItem.containsKey(item.getId())) {
                missing.add(item);
            }
        }
        return missing;
    }

    public void submit(LocalDateTime now, Collection<Item> inventory) throws AgreementStateException {
        if (isSubmitted()) {
            throw new AgreementStateException("This report was already submitted.");
        }
        List<Item> missing = missingItems(inventory);
        if (!missing.isEmpty()) {
            throw new AgreementStateException(missing.size() + " item(s) are not inspected yet, e.g. " + missing.get(0) + ".");
        }
        submittedAt = now;
    }

    public void confirmByLandlord() throws AgreementStateException {
        if (!isSubmitted()) {
            throw new AgreementStateException("The tenant has not submitted this report yet.");
        }
        landlordConfirmed = true;
    }

    /** The landlord disagrees: the report goes back to the tenant, who can re-photograph items and submit again. */
    public void sendBack(String comment) throws AgreementStateException {
        if (!isSubmitted() || landlordConfirmed) {
            throw new AgreementStateException("Only a submitted, unconfirmed report can be sent back.");
        }
        landlordComment = comment;
        submittedAt = null;
    }

    public boolean autoAcceptIfExpired(LocalDateTime now, int responseHours) {
        if (isSubmitted() && !landlordConfirmed && submittedAt.plusHours(responseHours).isBefore(now)) {
            landlordConfirmed = true;
            return true;
        }
        return false;
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    public boolean isFinal() {
        return isSubmitted() && landlordConfirmed;
    }

    public Optional<InspectionEntry> entryFor(String itemId) {
        return Optional.ofNullable(entriesByItem.get(itemId));
    }

    public Collection<InspectionEntry> getEntries() {
        return Collections.unmodifiableCollection(entriesByItem.values());
    }

    public String getId() {
        return id;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public Type getType() {
        return type;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public boolean isLandlordConfirmed() {
        return landlordConfirmed;
    }

    public String getLandlordComment() {
        return landlordComment;
    }
}
