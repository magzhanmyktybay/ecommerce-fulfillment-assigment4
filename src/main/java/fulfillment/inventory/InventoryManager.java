package fulfillment.inventory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service of Subsystem A (Inventory Manager). Reserves stock for an order,
 * releases it again (compensation) or marks it as sold.
 * Knows nothing about the warehouse or shipping subsystems.
 */
public class InventoryManager {

    private final InventoryStore store;
    private final OrderValidator validator = new OrderValidator();

    public InventoryManager(Path dataDir) {
        this.store = new InventoryStore(dataDir);
    }

    public StockReservation reserve(String orderId, String sku, int quantity) {
        validator.validate(sku, quantity);

        // Calling reserve again for the same order (e.g. a retry after a restart) does not reserve twice.
        Optional<StockReservation> existing = store.findReservation(orderId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!store.hasProduct(sku)) {
            throw new InventoryException("Unknown product: " + sku);
        }
        int available = store.getQuantity(sku);
        if (!validator.hasEnoughStock(available, quantity)) {
            throw new InventoryException("Not enough stock for " + sku
                    + ": requested " + quantity + " but only " + available + " available");
        }

        int total = validator.totalPrice(store.getUnitPrice(sku), quantity);
        StockReservation reservation = new StockReservation(orderId, sku, quantity, total, StockReservation.Status.RESERVED);
        store.setQuantity(sku, available - quantity);
        store.saveReservation(reservation);
        return reservation;
    }

    /** Compensation: gives the reserved items back to stock. Calling it twice is harmless. */
    public void release(String orderId) {
        StockReservation reservation = requireReservation(orderId);
        if (reservation.getStatus() == StockReservation.Status.RELEASED) {
            return;
        }
        reservation.release();
        store.setQuantity(reservation.getSku(), store.getQuantity(reservation.getSku()) + reservation.getQuantity());
        store.saveReservation(reservation);
    }

    /** Final step when the whole order succeeded. */
    public void markSold(String orderId) {
        StockReservation reservation = requireReservation(orderId);
        if (reservation.getStatus() == StockReservation.Status.SOLD) {
            return;
        }
        reservation.markSold();
        store.saveReservation(reservation);
    }

    public Optional<StockReservation> findReservation(String orderId) {
        return store.findReservation(orderId);
    }

    public List<StockReservation> findAllReservations() {
        return store.findAllReservations();
    }

    public Map<String, Integer> stockLevels() {
        return store.getAllQuantities();
    }

    private StockReservation requireReservation(String orderId) {
        return store.findReservation(orderId)
                .orElseThrow(() -> new InventoryException("No reservation for order " + orderId));
    }
}
