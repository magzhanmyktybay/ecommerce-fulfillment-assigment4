package fulfillment.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem A used directly, without any facade. Each test gets its own empty data folder. */
class InventoryManagerTest {

    @TempDir
    Path dataDir;

    private InventoryManager inventory;

    @BeforeEach
    void setUp() {
        new InventoryStore(dataDir).saveProduct("MOUSE", 8000, 10);
        inventory = new InventoryManager(dataDir);
    }

    @Test
    void reserveTakesItemsOutOfStockAndPersistsReservation() {
        StockReservation r = inventory.reserve("ORD-1", "MOUSE", 3);

        assertEquals(StockReservation.Status.RESERVED, r.getStatus());
        assertEquals(24000, r.getTotalPrice());
        assertEquals(7, inventory.stockLevels().get("MOUSE"));
        // a brand-new manager reads the same data back from disk
        InventoryManager afterRestart = new InventoryManager(dataDir);
        assertEquals(StockReservation.Status.RESERVED, afterRestart.findReservation("ORD-1").get().getStatus());
        assertEquals(7, afterRestart.stockLevels().get("MOUSE"));
    }

    @Test
    void reservingTheSameOrderTwiceDoesNotTakeStockTwice() {
        inventory.reserve("ORD-1", "MOUSE", 3);
        inventory.reserve("ORD-1", "MOUSE", 3);

        assertEquals(7, inventory.stockLevels().get("MOUSE"));
        assertEquals(1, inventory.findAllReservations().size());
    }

    @Test
    void notEnoughStockIsRefusedAndStockStaysUnchanged() {
        inventory.reserve("ORD-0", "MOUSE", 8);   // only 2 left

        InventoryException e = assertThrows(InventoryException.class, () -> inventory.reserve("ORD-1", "MOUSE", 5));
        assertTrue(e.getMessage().contains("only 2 available"));
        assertEquals(2, inventory.stockLevels().get("MOUSE"));
        assertTrue(inventory.findReservation("ORD-1").isEmpty());
    }

    @Test
    void unknownProductIsRefused() {
        assertThrows(InventoryException.class, () -> inventory.reserve("ORD-1", "TOASTER", 1));
    }

    @Test
    void invalidQuantityIsRefused() {
        assertThrows(InventoryException.class, () -> inventory.reserve("ORD-1", "MOUSE", 0));
        assertThrows(InventoryException.class, () -> inventory.reserve("ORD-1", "MOUSE", 11));
    }

    @Test
    void releaseGivesStockBackAndCanBeRepeatedSafely() {
        inventory.reserve("ORD-1", "MOUSE", 4);

        inventory.release("ORD-1");
        inventory.release("ORD-1");

        assertEquals(10, inventory.stockLevels().get("MOUSE"));
        assertEquals(StockReservation.Status.RELEASED, inventory.findReservation("ORD-1").get().getStatus());
    }

    @Test
    void markSoldIsFinal() {
        inventory.reserve("ORD-1", "MOUSE", 2);

        inventory.markSold("ORD-1");
        inventory.markSold("ORD-1");

        assertEquals(StockReservation.Status.SOLD, inventory.findReservation("ORD-1").get().getStatus());
        assertEquals(8, inventory.stockLevels().get("MOUSE"));
    }

    @Test
    void releasingAnUnknownOrderFails() {
        assertThrows(InventoryException.class, () -> inventory.release("ORD-404"));
        assertThrows(InventoryException.class, () -> inventory.markSold("ORD-404"));
    }

    @Test
    void storeSeedsDefaultProductsInAnEmptyFolder(@TempDir Path emptyDir) {
        InventoryStore store = new InventoryStore(emptyDir);

        assertTrue(store.hasProduct("LAPTOP"));
        assertEquals(350000, store.getUnitPrice("LAPTOP"));
        assertThrows(InventoryException.class, () -> store.getQuantity("TOASTER"));
    }
}
