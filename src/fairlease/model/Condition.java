package fairlease.model;

/** Declared from best to worst, so the declaration order is used to compare move-in and move-out states. */
public enum Condition {
    NEW, GOOD, WORN, DAMAGED;

    public boolean isWorseThan(Condition other) {
        return ordinal() > other.ordinal();
    }

    public boolean showsDamage() {
        return this == WORN || this == DAMAGED;
    }
}
