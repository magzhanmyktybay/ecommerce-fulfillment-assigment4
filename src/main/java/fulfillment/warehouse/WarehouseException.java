package fulfillment.warehouse;

/** Thrown when Subsystem B cannot pick or pack an order (e.g. the shelf bin is blocked). */
public class WarehouseException extends RuntimeException {
    public WarehouseException(String message) {
        super(message);
    }
}
