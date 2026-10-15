package fulfillment.shipping;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service of Subsystem C (Shipping Carrier API). Finalizes an order: checks that the carrier
 * delivers to the city, asks the payment gateway to approve the charge, charges the card and
 * hands the parcel to the carrier. Knows nothing about inventory or the warehouse.
 */
public class ShippingCarrierApi {

    public static final List<String> SERVED_CITIES = List.of("ASTANA", "ALMATY", "SHYMKENT", "KARAGANDA");
    public static final int BASE_SHIPPING_COST = 2000;
    public static final int COST_PER_EXTRA_ITEM = 500;

    private final ShippingStore store;
    private final PaymentGateway paymentGateway = new PaymentGateway();

    public ShippingCarrierApi(Path dataDir) {
        this.store = new ShippingStore(dataDir);
    }

    public int shippingCost(int quantity) {
        return BASE_SHIPPING_COST + COST_PER_EXTRA_ITEM * (quantity - 1);
    }

    public boolean deliversTo(String city) {
        return SERVED_CITIES.contains(city);
    }

    public Shipment ship(String orderId, String cardId, String city, int itemsTotal, int quantity) {
        // Calling it again for an already shipped order (e.g. a retry after a restart) does not charge twice.
        Optional<Shipment> existing = store.findShipment(orderId);
        if (existing.isPresent() && existing.get().getStatus() == Shipment.Status.SHIPPED) {
            return existing.get();
        }

        // Both checks happen BEFORE anything is written, so a C-failure leaves Subsystem C unchanged.
        if (!deliversTo(city)) {
            throw new ShippingException("Carrier does not deliver to " + city + ". Served cities: " + SERVED_CITIES);
        }
        if (!store.hasCard(cardId)) {
            throw new ShippingException("Unknown card: " + cardId);
        }
        int amount = itemsTotal + shippingCost(quantity);
        int balance = store.getBalance(cardId);
        paymentGateway.authorize(cardId, balance, amount);

        Shipment shipment = new Shipment(orderId, cardId, city, amount);
        store.saveCard(cardId, balance - amount);
        shipment.markPaid();
        store.saveShipment(shipment);
        shipment.ship("TRK-" + orderId);
        store.saveShipment(shipment);
        return shipment;
    }

    /**
     * Cancels a shipment and refunds the card if it was charged.
     * Returns true if something was cancelled, false if there was nothing to cancel.
     */
    public boolean cancelShipment(String orderId) {
        Optional<Shipment> found = store.findShipment(orderId);
        if (found.isEmpty() || found.get().getStatus() == Shipment.Status.CANCELLED) {
            return false;
        }
        Shipment shipment = found.get();
        if (shipment.isCharged()) {
            store.saveCard(shipment.getCardId(), store.getBalance(shipment.getCardId()) + shipment.getAmount());
        }
        shipment.cancel();
        store.saveShipment(shipment);
        return true;
    }

    public Optional<Shipment> findShipment(String orderId) {
        return store.findShipment(orderId);
    }

    public List<Shipment> findAllShipments() {
        return store.findAllShipments();
    }

    public Map<String, Integer> cardBalances() {
        return store.getAllBalances();
    }
}
