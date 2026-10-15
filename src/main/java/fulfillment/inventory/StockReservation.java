package fulfillment.inventory;

/**
 * Entity of Subsystem A: a quantity of one product held back for one order.
 * The entity itself decides which status changes are legal.
 */
public class StockReservation {

    public enum Status {
        RESERVED,   // stock taken off the shelf count, order still in progress
        SOLD,       // the whole order finished, the reservation is final
        RELEASED    // the order was compensated, stock was given back
    }

    private final String orderId;
    private final String sku;
    private final int quantity;
    private final int totalPrice;
    private Status status;

    public StockReservation(String orderId, String sku, int quantity, int totalPrice, Status status) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    /** RESERVED -> SOLD */
    public void markSold() {
        if (status != Status.RESERVED) {
            throw new IllegalStateException("Reservation " + orderId + " cannot become SOLD from " + status);
        }
        status = Status.SOLD;
    }

    /** RESERVED -> RELEASED (compensation) */
    public void release() {
        if (status != Status.RESERVED) {
            throw new IllegalStateException("Reservation " + orderId + " cannot be RELEASED from " + status);
        }
        status = Status.RELEASED;
    }

    public String getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public int getTotalPrice() { return totalPrice; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "Reservation[" + orderId + ", " + quantity + " x " + sku + ", " + totalPrice + " KZT, " + status + "]";
    }
}
