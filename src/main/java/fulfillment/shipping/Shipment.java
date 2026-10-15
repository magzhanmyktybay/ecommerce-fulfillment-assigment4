package fulfillment.shipping;

/**
 * Entity of Subsystem C: the paid and handed-over parcel of one order.
 * The entity itself decides which status changes are legal.
 */
public class Shipment {

    public enum Status {
        CREATED,    // shipment booked, not paid yet
        PAID,       // customer's card was charged
        SHIPPED,    // carrier took the parcel, tracking number assigned
        CANCELLED   // money refunded, shipment cancelled
    }

    private final String orderId;
    private final String cardId;
    private final String city;
    private final int amount;
    private String trackingNumber;
    private Status status;

    /** New shipment. */
    public Shipment(String orderId, String cardId, String city, int amount) {
        this(orderId, cardId, city, amount, "-", Status.CREATED);
    }

    /** Shipment loaded from the store. */
    public Shipment(String orderId, String cardId, String city, int amount, String trackingNumber, Status status) {
        this.orderId = orderId;
        this.cardId = cardId;
        this.city = city;
        this.amount = amount;
        this.trackingNumber = trackingNumber;
        this.status = status;
    }

    /** CREATED -> PAID */
    public void markPaid() {
        if (status != Status.CREATED) {
            throw new IllegalStateException("Shipment " + orderId + " cannot be PAID from " + status);
        }
        status = Status.PAID;
    }

    /** PAID -> SHIPPED */
    public void ship(String trackingNumber) {
        if (status != Status.PAID) {
            throw new IllegalStateException("Shipment " + orderId + " cannot be SHIPPED from " + status);
        }
        this.trackingNumber = trackingNumber;
        status = Status.SHIPPED;
    }

    /** CREATED / PAID / SHIPPED -> CANCELLED */
    public void cancel() {
        if (status == Status.CANCELLED) {
            throw new IllegalStateException("Shipment " + orderId + " is already CANCELLED");
        }
        status = Status.CANCELLED;
    }

    /** True when the card was charged and therefore a refund is needed on cancel. */
    public boolean isCharged() {
        return status == Status.PAID || status == Status.SHIPPED;
    }

    public String getOrderId() { return orderId; }
    public String getCardId() { return cardId; }
    public String getCity() { return city; }
    public int getAmount() { return amount; }
    public String getTrackingNumber() { return trackingNumber; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "Shipment[" + orderId + ", to " + city + ", " + amount + " KZT from " + cardId
                + ", tracking " + trackingNumber + ", " + status + "]";
    }
}
