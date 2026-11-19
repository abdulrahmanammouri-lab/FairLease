package fairlease.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class Payment {
    public enum Method { CARD, BANK_TRANSFER, FROM_DEPOSIT }

    private final String id;
    private final String agreementId;
    private final LocalDate date;
    private final double amount;
    private final Method method;
    private final List<String> paidIds;

    public Payment(String id, String agreementId, LocalDate date, double amount, Method method, List<String> paidIds) {
        this.id = id;
        this.agreementId = agreementId;
        this.date = date;
        this.amount = amount;
        this.method = method;
        this.paidIds = List.copyOf(paidIds);
    }

    public String getId() {
        return id;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public LocalDate getDate() {
        return date;
    }

    public double getAmount() {
        return amount;
    }

    public Method getMethod() {
        return method;
    }

    public List<String> getPaidIds() {
        return Collections.unmodifiableList(paidIds);
    }
}
