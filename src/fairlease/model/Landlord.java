package fairlease.model;

public class Landlord extends User {
    private final String iban;

    public Landlord(String id, String name, String email, String phone, String passwordHash, String iban) {
        super(id, name, email, phone, passwordHash);
        this.iban = iban;
    }

    public String getIban() {
        return iban;
    }

    @Override
    public Role getRole() {
        return Role.LANDLORD;
    }
}
