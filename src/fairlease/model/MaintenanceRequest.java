package fairlease.model;

import fairlease.exceptions.AgreementStateException;

import java.time.LocalDateTime;

public class MaintenanceRequest implements PhotoEvidence {
    public enum Urgency { LOW, NORMAL, URGENT }

    public enum Status { OPEN, SCHEDULED, IN_PROGRESS, SOLVED }

    private final String id;
    private final String agreementId;
    private final String description;
    private final Urgency urgency;
    private final Photo photo;
    private final LocalDateTime createdAt;
    private Status status;

    public MaintenanceRequest(String id, String agreementId, String description, Urgency urgency, Photo photo,
                              LocalDateTime createdAt, Status status) {
        this.id = id;
        this.agreementId = agreementId;
        this.description = description;
        this.urgency = urgency;
        this.photo = photo;
        this.createdAt = createdAt;
        this.status = status;
    }

    public void moveTo(Status next) throws AgreementStateException {
        if (next.ordinal() <= status.ordinal()) {
            throw new AgreementStateException("A repair can only move forward (it is " + status + ", not " + next + ").");
        }
        status = next;
    }

    public boolean needsEscalation(LocalDateTime now, int hours) {
        return urgency == Urgency.URGENT && status == Status.OPEN && createdAt.plusHours(hours).isBefore(now);
    }

    public String getId() {
        return id;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public String getDescription() {
        return description;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    @Override
    public Photo getPhoto() {
        return photo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Status getStatus() {
        return status;
    }
}
