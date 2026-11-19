package fairlease.model;

public enum BillType {
    RENT, WATER, ELECTRICITY, GAS, INTERNET, MAINTENANCE;

    public boolean isUtility() {
        return this != RENT;
    }
}
