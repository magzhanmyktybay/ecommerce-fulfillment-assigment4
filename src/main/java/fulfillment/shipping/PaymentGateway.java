package fulfillment.shipping;

/**
 * Pure business logic of Subsystem C: the payment decision. No file access.
 * The card balance is read by the caller and passed in.
 */
public class PaymentGateway {

    public boolean isApproved(int balance, int amount) {
        return amount > 0 && balance >= amount;
    }

    /** Throws if the charge is declined. */
    public void authorize(String cardId, int balance, int amount) {
        if (!isApproved(balance, amount)) {
            throw new ShippingException("Payment declined for card " + cardId
                    + ": needs " + amount + " KZT, balance is " + balance + " KZT");
        }
    }
}
