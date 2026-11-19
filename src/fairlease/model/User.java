package fairlease.model;

import fairlease.util.Hashing;

public abstract class User {
    private final String id;
    private final String name;
    private final String email;
    private final String phone;
    private final String passwordHash;

    protected User(String id, String name, String email, String phone, String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public static String hashPassword(String email, String password) {
        return Hashing.sha256(email.toLowerCase() + ":" + password);
    }

    public boolean checkPassword(String password) {
        return passwordHash.equals(hashPassword(email, password));
    }

    public abstract Role getRole();

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    @Override
    public String toString() {
        return name + " <" + email + ">";
    }
}
