package fulfillment.warehouse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem B used directly, without any facade. */
class WarehousePickerTest {

    @TempDir
    Path dataDir;

    private WarehousePicker picker;

    @BeforeEach
    void setUp() {
        picker = new WarehousePicker(dataDir);
    }

    @Test
    void pickAndPackPersistsAPackedTask() {
        PickTask task = picker.pickAndPack("ORD-1", "MOUSE", 3);

        assertEquals(PickTask.Status.PACKED, task.getStatus());
        assertEquals("MEDIUM", task.getBoxSize());
        PickTask reloaded = new WarehousePicker(dataDir).findTask("ORD-1").get();
        assertEquals(PickTask.Status.PACKED, reloaded.getStatus());
        assertEquals("MEDIUM", reloaded.getBoxSize());
    }

    @Test
    void repeatedPickForAPackedOrderIsIgnored() {
        picker.pickAndPack("ORD-1", "MOUSE", 1);
        picker.pickAndPack("ORD-1", "MOUSE", 1);

        assertEquals(1, picker.findAllTasks().size());
    }

    @Test
    void blockedShelfBinMakesPickingFailWithoutSavingATask() {
        picker.blockShelfBin("MOUSE");

        WarehouseException e = assertThrows(WarehouseException.class, () -> picker.pickAndPack("ORD-1", "MOUSE", 1));
        assertTrue(e.getMessage().contains("blocked"));
        assertTrue(picker.findTask("ORD-1").isEmpty());
    }

    @Test
    void blockedBinsArePersistedAndCanBeUnblocked() {
        picker.blockShelfBin("MOUSE");
        assertTrue(new WarehousePicker(dataDir).blockedShelfBins().contains("MOUSE"));

        picker.unblockShelfBin("MOUSE");
        assertTrue(new WarehousePicker(dataDir).blockedShelfBins().isEmpty());
        assertEquals(PickTask.Status.PACKED, picker.pickAndPack("ORD-1", "MOUSE", 1).getStatus());
    }

    @Test
    void cancelTaskMarksItCancelledOnlyOnce() {
        picker.pickAndPack("ORD-1", "MOUSE", 1);

        assertTrue(picker.cancelTask("ORD-1"));
        assertFalse(picker.cancelTask("ORD-1"));
        assertEquals(PickTask.Status.CANCELLED, picker.findTask("ORD-1").get().getStatus());
    }

    @Test
    void cancellingAnUnknownTaskDoesNothing() {
        assertFalse(picker.cancelTask("ORD-404"));
    }

    @Test
    void cancelledTaskCannotBePickedAgain() {
        picker.pickAndPack("ORD-1", "MOUSE", 1);
        picker.cancelTask("ORD-1");

        assertThrows(WarehouseException.class, () -> picker.pickAndPack("ORD-1", "MOUSE", 1));
    }
}
