package fairlease.model;

import java.time.LocalDateTime;

public final class CallLogEntry {
    private final String disputeId;
    private final LocalDateTime at;
    private final String agentId;
    private final String summary;

    public CallLogEntry(String disputeId, LocalDateTime at, String agentId, String summary) {
        this.disputeId = disputeId;
        this.at = at;
        this.agentId = agentId;
        this.summary = summary;
    }

    public String getDisputeId() {
        return disputeId;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getSummary() {
        return summary;
    }
}
