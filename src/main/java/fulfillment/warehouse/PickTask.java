package fulfillment.warehouse;

/**
 * Entity of Subsystem B: the job of picking one order's items from a shelf and packing them.
 * The entity itself decides which status changes are legal.
 */
public class PickTask {

    public enum Status {
        CREATED,    // task exists, nothing picked yet
        PICKED,     // items taken from the shelf
        PACKED,     // items are in a box, ready for the carrier
        CANCELLED   // compensation: box opened, items put back on the shelf
    }

    private final String orderId;
    private final String sku;
    private final int quantity;
    private String boxSize;
    private Status status;

    /** New task. */
    public PickTask(String orderId, String sku, int quantity) {
        this(orderId, sku, quantity, "-", Status.CREATED);
    }

    /** Task loaded from the store. */
    public PickTask(String orderId, String sku, int quantity, String boxSize, Status status) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.boxSize = boxSize;
        this.status = status;
    }

    /** CREATED -> PICKED */
    public void pick() {
        if (status != Status.CREATED) {
            throw new IllegalStateException("Task " + orderId + " cannot be PICKED from " + status);
        }
        status = Status.PICKED;
    }

    /** PICKED -> PACKED */
    public void pack(String boxSize) {
        if (status != Status.PICKED) {
            throw new IllegalStateException("Task " + orderId + " cannot be PACKED from " + status);
        }
        this.boxSize = boxSize;
        status = Status.PACKED;
    }

    /** CREATED / PICKED / PACKED -> CANCELLED */
    public void cancel() {
        if (status == Status.CANCELLED) {
            throw new IllegalStateException("Task " + orderId + " is already CANCELLED");
        }
        status = Status.CANCELLED;
    }

    public String getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public String getBoxSize() { return boxSize; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "PickTask[" + orderId + ", " + quantity + " x " + sku + ", box " + boxSize + ", " + status + "]";
    }
}
