package fairlease.model;

public class CallCenterAgent extends User {
    public CallCenterAgent(String id, String name, String email, String phone, String passwordHash) {
        super(id, name, email, phone, passwordHash);
    }

    @Override
    public Role getRole() {
        return Role.AGENT;
    }
}
