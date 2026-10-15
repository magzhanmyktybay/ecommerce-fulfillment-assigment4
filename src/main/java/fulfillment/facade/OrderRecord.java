package fulfillment.facade;

/**
 * The persisted progress of one order through the A -> B -> C chain.
 * After a restart the facades read this status from disk to know what is done and what is not.
 */
public class OrderRecord {

    public enum Status {
        PENDING_B,    // A committed (stock reserved), B not done yet
        PENDING_C,    // A and B committed (reserved + packed), C not done yet
        COMPLETED,    // A, B and C committed
        COMPENSATED,  // B or C failed, the committed steps were undone
        REJECTED      // A refused the order, nothing was committed
    }

    private final String orderId;
    private final String sku;
    private final int quantity;
    private final String city;
    private final String cardId;
    private final int itemsTotal;
    private Status status;
    private String message;

    public OrderRecord(String orderId, String sku, int quantity, String city, String cardId,
                       int itemsTotal, Status status, String message) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.city = city;
        this.cardId = cardId;
        this.itemsTotal = itemsTotal;
        this.status = status;
        this.message = message;
    }

    /** Only forward moves of the chain are legal. */
    public void moveTo(Status next, String message) {
        boolean legal = (status == Status.PENDING_B && (next == Status.PENDING_C || next == Status.COMPENSATED))
                || (status == Status.PENDING_C && (next == Status.COMPLETED || next == Status.COMPENSATED));
        if (!legal) {
            throw new IllegalStateException("Order " + orderId + " cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.message = message;
    }

    public boolean isInterrupted() {
        return status == Status.PENDING_B || status == Status.PENDING_C;
    }

    public String getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public String getCity() { return city; }
    public String getCardId() { return cardId; }
    public int getItemsTotal() { return itemsTotal; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return orderId + " | " + quantity + " x " + sku + " -> " + city + " | card " + cardId
                + " | items " + itemsTotal + " KZT | " + status + " | " + message;
    }
}
