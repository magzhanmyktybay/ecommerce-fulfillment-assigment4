package fulfillment.inventory;

/** Thrown when Subsystem A refuses a request (invalid order line, unknown product, not enough stock). */
public class InventoryException extends RuntimeException {
    public InventoryException(String message) {
        super(message);
    }
}
