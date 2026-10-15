package fulfillment.facade;

import fulfillment.inventory.StockReservation;
import fulfillment.shipping.ShippingCarrierApi;
import fulfillment.warehouse.PickTask;
import fulfillment.warehouse.WarehousePicker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static fulfillment.facade.ShopTestKit.aShopIn;
import static org.junit.jupiter.api.Assertions.*;

class FulfillmentAdminFacadeTest {

    @TempDir
    Path dataDir;

    private ShopTestKit shop;

    @BeforeEach
    void givenAShopWithMiceAndATestCard() {
        shop = aShopIn(dataDir).withStock("MOUSE", 8000, 10).withCard("CARD-T", 100000);
    }

    @Test
    void inspectShowsTheOrderInAllThreeSubsystems() {
        OrderRecord order = shop.customerOrders(1, "MOUSE").paidWith("CARD-T");

        String report = shop.admin().inspectOrder(order.getOrderId());

        assertTrue(report.contains("COMPLETED"));
        assertTrue(report.contains("SOLD"));
        assertTrue(report.contains("PACKED"));
        assertTrue(report.contains("SHIPPED"));
    }

    @Test
    void inspectSaysWhatIsMissing() {
        String orderId = shop.customerOrders(1, "MOUSE").paidWithButCrashAfterStepA("CARD-T");

        String report = shop.admin().inspectOrder(orderId);

        assertTrue(report.contains("no pick task"));
        assertTrue(report.contains("no shipment"));
        assertThrows(IllegalArgumentException.class, () -> shop.admin().inspectOrder("ORD-404"));
    }

    @Test
    void onlyHalfFinishedOrdersAreListedAsInterrupted() {
        shop.customerOrders(1, "MOUSE").paidWith("CARD-T");
        String crashed = shop.customerOrders(1, "MOUSE").paidWithButCrashAfterStepA("CARD-T");

        List<OrderRecord> interrupted = shop.admin().listInterruptedOrders();

        assertEquals(2, shop.admin().listOrders().size());
        assertEquals(1, interrupted.size());
        assertEquals(crashed, interrupted.get(0).getOrderId());
    }

    @Test
    void manualCompensationAfterACrashBehindStepAReleasesTheStock() {
        String orderId = shop.customerOrders(3, "MOUSE").paidWithButCrashAfterStepA("CARD-T");

        List<String> steps = shop.admin().compensateManually(orderId);

        assertEquals(1, steps.size());
        shop.assertOrderIs(orderId, OrderRecord.Status.COMPENSATED);
        shop.assertStockOf("MOUSE", 10);
    }

    @Test
    void manualCompensationAfterACrashBehindStepBUndoesBThenA() {
        String orderId = shop.customerOrders(3, "MOUSE").paidWithButCrashAfterStepB("CARD-T");

        List<String> steps = shop.admin().compensateManually(orderId);

        assertTrue(steps.get(0).startsWith("B:"));
        assertTrue(steps.get(1).startsWith("A:"));
        shop.assertPickTaskIs(orderId, PickTask.Status.CANCELLED);
        shop.assertReservationIs(orderId, StockReservation.Status.RELEASED);
    }

    @Test
    void crashRightAfterStepCIsUndoneInTheOrderCThenBThenA() {
        String orderId = shop.customerOrders(2, "MOUSE").paidWithButCrashAfterStepB("CARD-T");
        new ShippingCarrierApi(dataDir).ship(orderId, "CARD-T", "ASTANA", 16000, 2);   // C committed, status still PENDING_C

        List<String> steps = shop.admin().compensateManually(orderId);

        assertEquals(3, steps.size());
        assertTrue(steps.get(0).startsWith("C:"));
        assertTrue(steps.get(1).startsWith("B:"));
        assertTrue(steps.get(2).startsWith("A:"));
        shop.assertBalanceOf("CARD-T", 100000);
        shop.assertStockOf("MOUSE", 10);
    }

    @Test
    void crashBetweenBSavingAndStatusUpdateIsHandled() {
        // B saved its task, but the app died before the workflow said PENDING_C
        String orderId = shop.customerOrders(1, "MOUSE").paidWithButCrashAfterStepA("CARD-T");
        new WarehousePicker(dataDir).pickAndPack(orderId, "MOUSE", 1);

        List<String> steps = shop.admin().compensateManually(orderId);

        assertEquals(2, steps.size());
        shop.assertPickTaskIs(orderId, PickTask.Status.CANCELLED);
        shop.assertStockOf("MOUSE", 10);
    }

    @Test
    void finishedOrdersCannotBeCompensated() {
        OrderRecord order = shop.customerOrders(1, "MOUSE").paidWith("CARD-T");

        assertThrows(IllegalStateException.class, () -> shop.admin().compensateManually(order.getOrderId()));
        assertThrows(IllegalArgumentException.class, () -> shop.admin().compensateManually("ORD-404"));
    }

    @Test
    void adminCanBlockAndUnblockShelfBinsAndSeeStockAndCards() {
        FulfillmentAdminFacade admin = shop.admin();

        admin.blockShelfBin(" mouse ");
        assertTrue(shop.admin().blockedShelfBins().contains("MOUSE"));
        admin.unblockShelfBin("MOUSE");
        assertTrue(shop.admin().blockedShelfBins().isEmpty());

        assertEquals(10, admin.stockLevels().get("MOUSE"));
        assertEquals(100000, admin.cardBalances().get("CARD-T"));
    }
}
