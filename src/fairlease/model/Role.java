package fairlease.model;

public enum Role {
    TENANT("Tenant"),
    LANDLORD("Landlord"),
    AGENT("Call center agent");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
