package fairlease.model;

import java.time.LocalDateTime;

/** A chat message between tenant and landlord. Messages are immutable and never deleted, so they stay as proof. */
public final class Message {
    private final String agreementId;
    private final String senderId;
    private final LocalDateTime sentAt;
    private final String text;

    public Message(String agreementId, String senderId, LocalDateTime sentAt, String text) {
        this.agreementId = agreementId;
        this.senderId = senderId;
        this.sentAt = sentAt;
        this.text = text;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public String getSenderId() {
        return senderId;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public String getText() {
        return text;
    }
}
