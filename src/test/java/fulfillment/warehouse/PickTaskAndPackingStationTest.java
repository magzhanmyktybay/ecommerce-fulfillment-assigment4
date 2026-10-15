package fulfillment.warehouse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PickTaskAndPackingStationTest {

    private final PackingStation station = new PackingStation();

    @Test
    void boxSizeDependsOnQuantity() {
        assertEquals("SMALL", station.chooseBox(2));
        assertEquals("MEDIUM", station.chooseBox(5));
        assertEquals("LARGE", station.chooseBox(6));
        assertThrows(IllegalArgumentException.class, () -> station.chooseBox(0));
    }

    @Test
    void taskCannotBePackedBeforeItIsPicked() {
        PickTask task = new PickTask("ORD-1", "MOUSE", 1);
        assertThrows(IllegalStateException.class, () -> task.pack("SMALL"));
    }

    @Test
    void taskCannotBePickedTwice() {
        PickTask task = new PickTask("ORD-1", "MOUSE", 1);
        task.pick();
        assertThrows(IllegalStateException.class, task::pick);
    }

    @Test
    void cancelledTaskCannotBeCancelledAgain() {
        PickTask task = new PickTask("ORD-1", "MOUSE", 1);
        task.cancel();
        assertThrows(IllegalStateException.class, task::cancel);
        assertTrue(task.toString().contains("CANCELLED"));
    }
}
