package fulfillment.inventory;

/**
 * Pure business rules of Subsystem A. No state, no file access.
 */
public class OrderValidator {

    public static final int MAX_QUANTITY_PER_ORDER = 10;

    /** Checks the order line itself, before any stock is touched. */
    public void validate(String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new InventoryException("Product code (SKU) must not be empty");
        }
        if (!sku.matches("[A-Z0-9-]+")) {
            throw new InventoryException("Product code may contain only A-Z, 0-9 and '-': " + sku);
        }
        if (quantity < 1) {
            throw new InventoryException("Quantity must be at least 1, got " + quantity);
        }
        if (quantity > MAX_QUANTITY_PER_ORDER) {
            throw new InventoryException("Quantity must be at most " + MAX_QUANTITY_PER_ORDER + ", got " + quantity);
        }
    }

    public boolean hasEnoughStock(int available, int requested) {
        return available >= requested;
    }

    public int totalPrice(int unitPrice, int quantity) {
        return unitPrice * quantity;
    }
}
