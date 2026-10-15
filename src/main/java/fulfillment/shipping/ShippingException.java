package fulfillment.shipping;

/** Thrown when Subsystem C cannot finish an order (carrier refuses the address, payment declined). */
public class ShippingException extends RuntimeException {
    public ShippingException(String message) {
        super(message);
    }
}
