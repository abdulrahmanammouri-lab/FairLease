package fairlease.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Property {
    public enum Type { APARTMENT, HOUSE, STUDIO, ROOM }

    public enum Status { FREE, RENTED, IN_DISPUTE }

    public static final String METER_ROOM = "Meters";

    private final String id;
    private final String landlordId;
    private final Type type;
    private final String address;
    private final double surface;
    private Status status;
    private final List<Item> items = new ArrayList<>();

    public Property(String id, String landlordId, Type type, String address, double surface, Status status) {
        this.id = id;
        this.landlordId = landlordId;
        this.type = type;
        this.address = address;
        this.surface = surface;
        this.status = status;
    }

    public void addItem(Item item) {
        if (!item.getPropertyId().equals(id)) {
            throw new IllegalArgumentException("Item " + item.getId() + " belongs to another property");
        }
        items.add(item);
    }

    /** Rooms in the order their first item was added, so the inspection walks through the house in a stable order. */
    public Set<String> getRooms() {
        Set<String> rooms = new LinkedHashSet<>();
        for (Item item : items) {
            rooms.add(item.getRoom());
        }
        return rooms;
    }

    public List<Item> getItemsIn(String room) {
        List<Item> result = new ArrayList<>();
        for (Item item : items) {
            if (item.getRoom().equals(room)) {
                result.add(item);
            }
        }
        return result;
    }

    public Optional<Item> findItem(String itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst();
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    public String getId() {
        return id;
    }

    public String getLandlordId() {
        return landlordId;
    }

    public Type getType() {
        return type;
    }

    public String getAddress() {
        return address;
    }

    public double getSurface() {
        return surface;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return address + " (" + type + ", " + surface + " m2)";
    }
}
