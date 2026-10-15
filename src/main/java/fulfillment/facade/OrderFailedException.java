package fulfillment.facade;

import java.util.List;

/** Step B or C failed after earlier steps had committed. The facade has already compensated them. */
public class OrderFailedException extends RuntimeException {

    private final String orderId;
    private final List<String> compensationSteps;

    public OrderFailedException(String orderId, String reason, List<String> compensationSteps) {
        super("Order " + orderId + " failed: " + reason);
        this.orderId = orderId;
        this.compensationSteps = compensationSteps;
    }

    public String getOrderId() {
        return orderId;
    }

    /** The undo actions in the order they were executed, e.g. ["B: ...", "A: ..."]. */
    public List<String> getCompensationSteps() {
        return compensationSteps;
    }
}
