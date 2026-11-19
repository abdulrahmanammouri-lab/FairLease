package fairlease.model;

public class Tenant extends User {
    public Tenant(String id, String name, String email, String phone, String passwordHash) {
        super(id, name, email, phone, passwordHash);
    }

    @Override
    public Role getRole() {
        return Role.TENANT;
    }
}
