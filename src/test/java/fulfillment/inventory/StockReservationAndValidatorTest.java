package fulfillment.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockReservationAndValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    private StockReservation reserved() {
        return new StockReservation("ORD-1", "MOUSE", 1, 8000, StockReservation.Status.RESERVED);
    }

    @Test
    void soldReservationCannotBeReleased() {
        StockReservation r = reserved();
        r.markSold();
        assertThrows(IllegalStateException.class, r::release);
    }

    @Test
    void releasedReservationCannotBeSold() {
        StockReservation r = reserved();
        r.release();
        assertThrows(IllegalStateException.class, r::markSold);
        assertTrue(r.toString().contains("RELEASED"));
    }

    @Test
    void validatorChecksSkuAndQuantity() {
        assertThrows(InventoryException.class, () -> validator.validate(null, 1));
        assertThrows(InventoryException.class, () -> validator.validate("  ", 1));
        assertThrows(InventoryException.class, () -> validator.validate("MO,USE", 1));
        validator.validate("MOUSE", 10);   // no exception = valid
    }

    @Test
    void validatorComputesStockDecisionAndPrice() {
        assertTrue(validator.hasEnoughStock(5, 5));
        assertFalse(validator.hasEnoughStock(4, 5));
        assertEquals(24000, validator.totalPrice(8000, 3));
    }
}
