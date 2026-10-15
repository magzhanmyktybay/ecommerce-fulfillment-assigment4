package fulfillment.facade;

import fulfillment.inventory.StockReservation;
import fulfillment.shipping.Shipment;
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

class OrderFulfillmentFacadeTest {

    @TempDir
    Path dataDir;

    private ShopTestKit shop;

    @BeforeEach
    void givenAShopWithMiceAndATestCard() {
        // MOUSE costs 8000, shipping 2 items costs 2500 -> an order of 2 mice costs 18500
        shop = aShopIn(dataDir).withStock("MOUSE", 8000, 10).withCard("CARD-T", 100000);
    }

    // ---------- happy path ----------

    @Test
    void successfulOrderCommitsAllThreeSubsystems() {
        OrderRecord order = shop.customerOrders(2, "MOUSE").shippedTo("ALMATY").paidWith("CARD-T");

        shop.assertOrderIs(order.getOrderId(), OrderRecord.Status.COMPLETED);
        shop.assertReservationIs(order.getOrderId(), StockReservation.Status.SOLD);
        shop.assertPickTaskIs(order.getOrderId(), PickTask.Status.PACKED);
        assertEquals(Shipment.Status.SHIPPED,
                new ShippingCarrierApi(dataDir).findShipment(order.getOrderId()).get().getStatus());
        shop.assertStockOf("MOUSE", 8);
        shop.assertBalanceOf("CARD-T", 100000 - 18500);
    }

    @Test
    void inputIsNormalizedAndOrderIdsKeepCountingAfterRestart() {
        OrderRecord first = shop.customerOrders(1, " mouse ").shippedTo("astana").paidWith("card-t");
        OrderRecord second = shop.customerOrders(1, "MOUSE").paidWith("CARD-T");

        assertEquals("ORD-1001", first.getOrderId());
        assertEquals("ORD-1002", second.getOrderId());
        assertEquals("MOUSE", first.getSku());
    }

    // ---------- extra failure case: rejected at step A, nothing committed ----------

    @Test
    void orderWithoutEnoughStockIsRejectedBeforeAnythingIsCommitted() {
        OrderRejectedException e = assertThrows(OrderRejectedException.class,
                () -> shop.withStock("MOUSE", 8000, 1).customerOrders(2, "MOUSE").paidWith("CARD-T"));

        shop.assertOrderIs(e.getOrderId(), OrderRecord.Status.REJECTED);
        shop.assertStockOf("MOUSE", 1);
        shop.assertNoPickTask(e.getOrderId());
        shop.assertNoShipment(e.getOrderId());
        shop.assertBalanceOf("CARD-T", 100000);
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(OrderRejectedException.class, () -> shop.customerOrders(0, "MOUSE").paidWith("CARD-T"));
        assertThrows(OrderRejectedException.class, () -> shop.customerOrders(1, "MOUSE").shippedTo(" ").paidWith("CARD-T"));
        assertThrows(OrderRejectedException.class, () -> shop.customerOrders(1, "MOUSE").paidWith(null));
        shop.assertStockOf("MOUSE", 10);
    }

    // ---------- compensation depth 1: B fails after A committed ----------

    @Test
    void whenPickingFailsOnlyTheStockReservationIsUndone() {
        shop.withBlockedShelfFor("MOUSE");

        OrderFailedException e = assertThrows(OrderFailedException.class,
                () -> shop.customerOrders(2, "MOUSE").paidWith("CARD-T"));

        assertEquals(1, e.getCompensationSteps().size());
        assertTrue(e.getCompensationSteps().get(0).startsWith("A:"));
        shop.assertOrderIs(e.getOrderId(), OrderRecord.Status.COMPENSATED);
        shop.assertReservationIs(e.getOrderId(), StockReservation.Status.RELEASED);
        shop.assertStockOf("MOUSE", 10);
        shop.assertNoPickTask(e.getOrderId());
        shop.assertNoShipment(e.getOrderId());
    }

    // ---------- compensation depth 2: C fails after A and B committed ----------

    @Test
    void whenPaymentIsDeclinedPackingIsUndoneFirstAndThenTheReservation() {
        shop.withCard("CARD-POOR", 100);

        OrderFailedException e = assertThrows(OrderFailedException.class,
                () -> shop.customerOrders(2, "MOUSE").shippedTo("ALMATY").paidWith("CARD-POOR"));

        assertEquals(List.of("B", "A"), List.of(
                e.getCompensationSteps().get(0).substring(0, 1),
                e.getCompensationSteps().get(1).substring(0, 1)));
        shop.assertOrderIs(e.getOrderId(), OrderRecord.Status.COMPENSATED);
        shop.assertPickTaskIs(e.getOrderId(), PickTask.Status.CANCELLED);
        shop.assertReservationIs(e.getOrderId(), StockReservation.Status.RELEASED);
        shop.assertStockOf("MOUSE", 10);
        shop.assertNoShipment(e.getOrderId());
        shop.assertBalanceOf("CARD-POOR", 100);
    }

    @Test
    void whenTheCarrierDoesNotDeliverToTheCityBAndAAreUndone() {
        OrderFailedException e = assertThrows(OrderFailedException.class,
                () -> shop.customerOrders(1, "MOUSE").shippedTo("MOON").paidWith("CARD-T"));

        assertTrue(e.getMessage().contains("step C failed"));
        assertEquals(2, e.getCompensationSteps().size());
        shop.assertPickTaskIs(e.getOrderId(), PickTask.Status.CANCELLED);
        shop.assertReservationIs(e.getOrderId(), StockReservation.Status.RELEASED);
        shop.assertStockOf("MOUSE", 10);
        shop.assertBalanceOf("CARD-T", 100000);
    }

    // ---------- persistence reload: restart in the middle of the chain ----------

    @Test
    void afterARestartBehindStepATheOrderIsResumedFromDisk() {
        String orderId = shop.customerOrders(2, "MOUSE").paidWithButCrashAfterStepA("CARD-T");
        shop.assertOrderIs(orderId, OrderRecord.Status.PENDING_B);

        OrderRecord resumed = shop.restartApp().resume(orderId);

        assertEquals(OrderRecord.Status.COMPLETED, resumed.getStatus());
        shop.assertReservationIs(orderId, StockReservation.Status.SOLD);
        shop.assertPickTaskIs(orderId, PickTask.Status.PACKED);
        shop.assertStockOf("MOUSE", 8);
        shop.assertBalanceOf("CARD-T", 100000 - 18500);
    }

    @Test
    void afterARestartBehindStepBOnlyStepCIsRun() {
        String orderId = shop.customerOrders(2, "MOUSE").paidWithButCrashAfterStepB("CARD-T");

        OrderRecord resumed = shop.restartApp().resume(orderId);

        assertEquals(OrderRecord.Status.COMPLETED, resumed.getStatus());
        assertEquals(1, new WarehousePicker(dataDir).findAllTasks().size());   // B was not repeated
        shop.assertStockOf("MOUSE", 8);                                        // A was not repeated
    }

    @Test
    void afterARestartBehindStepBAFailingStepCStillUndoesBThenA() {
        shop.withCard("CARD-POOR", 100);
        String orderId = shop.customerOrders(2, "MOUSE").paidWithButCrashAfterStepB("CARD-POOR");

        OrderFailedException e = assertThrows(OrderFailedException.class, () -> shop.restartApp().resume(orderId));

        assertTrue(e.getCompensationSteps().get(0).startsWith("B:"));
        assertTrue(e.getCompensationSteps().get(1).startsWith("A:"));
        shop.assertOrderIs(orderId, OrderRecord.Status.COMPENSATED);
        shop.assertStockOf("MOUSE", 10);
    }

    @Test
    void resumingAFinishedOrderChangesNothing() {
        OrderRecord order = shop.customerOrders(1, "MOUSE").paidWith("CARD-T");

        OrderRecord again = shop.restartApp().resume(order.getOrderId());

        assertEquals(OrderRecord.Status.COMPLETED, again.getStatus());
        shop.assertStockOf("MOUSE", 9);
        shop.assertBalanceOf("CARD-T", 100000 - 10000);
    }

    @Test
    void resumingAnUnknownOrderFails() {
        assertThrows(IllegalArgumentException.class, () -> shop.restartApp().resume("ORD-404"));
    }

    @Test
    void workflowStatusCannotGoBackwards() {
        OrderRecord order = shop.customerOrders(1, "MOUSE").paidWith("CARD-T");
        assertThrows(IllegalStateException.class, () -> order.moveTo(OrderRecord.Status.PENDING_C, "back"));
    }
}
