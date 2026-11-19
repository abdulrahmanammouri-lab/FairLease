package fairlease.model;

/** A piece of furniture, an appliance, a fixture or a utility meter that is inspected at move-in and move-out. */
public class Item {
    public enum Category { FURNITURE, APPLIANCE, FIXTURE, METER }

    private final String id;
    private final String propertyId;
    private final String room;
    private final String name;
    private final Category category;

    public Item(String id, String propertyId, String room, String name, Category category) {
        this.id = id;
        this.propertyId = propertyId;
        this.room = room;
        this.name = name;
        this.category = category;
    }

    public boolean isMeter() {
        return category == Category.METER;
    }

    public String getId() {
        return id;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getRoom() {
        return room;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return room + " / " + name;
    }
}
