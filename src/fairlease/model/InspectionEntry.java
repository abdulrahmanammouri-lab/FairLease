package fairlease.model;

/** The state of one item at move-in or move-out, with its photo and, for meters, the reading. */
public class InspectionEntry implements PhotoEvidence {
    private final String itemId;
    private final Condition condition;
    private final String note;
    private final Double meterReading;
    private final Photo photo;

    public InspectionEntry(String itemId, Condition condition, String note, Double meterReading, Photo photo) {
        this.itemId = itemId;
        this.condition = condition;
        this.note = note;
        this.meterReading = meterReading;
        this.photo = photo;
    }

    public String getItemId() {
        return itemId;
    }

    public Condition getCondition() {
        return condition;
    }

    public String getNote() {
        return note;
    }

    public Double getMeterReading() {
        return meterReading;
    }

    @Override
    public Photo getPhoto() {
        return photo;
    }
}
