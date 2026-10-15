package fulfillment.facade;

/** Step A (or the input check before it) refused the order. Nothing was committed, nothing to undo. */
public class OrderRejectedException extends RuntimeException {

    private final String orderId;

    public OrderRejectedException(String orderId, String reason) {
        super("Order " + orderId + " rejected: " + reason);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
