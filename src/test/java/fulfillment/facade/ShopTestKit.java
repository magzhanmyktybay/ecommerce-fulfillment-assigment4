package fulfillment.facade;

import fulfillment.inventory.InventoryManager;
import fulfillment.inventory.InventoryStore;
import fulfillment.inventory.StockReservation;
import fulfillment.shipping.ShippingCarrierApi;
import fulfillment.shipping.ShippingStore;
import fulfillment.warehouse.PickTask;
import fulfillment.warehouse.WarehousePicker;
import fulfillment.warehouse.WarehouseStore;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain-specific test language. Lets a test read like a sentence, e.g.
 *
 *   shop.withStock("MOUSE", 8000, 10).withBlockedShelfFor("MOUSE");
 *   shop.customerOrders(2, "MOUSE").shippedTo("ALMATY").paidWith("CARD-T");
 *   shop.assertStockOf("MOUSE", 10);
 *
 * Every "when" step creates NEW facade objects on the same folder, so state is always read
 * from disk - exactly like after a restart of the application.
 */
class ShopTestKit {

    private final Path dataDir;

    private ShopTestKit(Path dataDir) {
        this.dataDir = dataDir;
    }

    static ShopTestKit aShopIn(Path dataDir) {
        return new ShopTestKit(dataDir);
    }

    // ---------- given ----------

    ShopTestKit withStock(String sku, int unitPrice, int quantity) {
        new InventoryStore(dataDir).saveProduct(sku, unitPrice, quantity);
        return this;
    }

    ShopTestKit withCard(String cardId, int balance) {
        new ShippingStore(dataDir).saveCard(cardId, balance);
        return this;
    }

    ShopTestKit withBlockedShelfFor(String sku) {
        new WarehouseStore(dataDir).blockSku(sku);
        return this;
    }

    // ---------- when ----------

    OrderDraft customerOrders(int quantity, String sku) {
        return new OrderDraft(quantity, sku);
    }

    /** Fresh facade objects = a simulated restart of the application. */
    OrderFulfillmentFacade restartApp() {
        return new OrderFulfillmentFacade(dataDir);
    }

    FulfillmentAdminFacade admin() {
        return new FulfillmentAdminFacade(dataDir);
    }

    class OrderDraft {
        private final int quantity;
        private final String sku;
        private String city = "ASTANA";

        OrderDraft(int quantity, String sku) {
            this.quantity = quantity;
            this.sku = sku;
        }

        OrderDraft shippedTo(String city) {
            this.city = city;
            return this;
        }

        /** Runs the whole A -> B -> C chain. */
        OrderRecord paidWith(String cardId) {
            return restartApp().placeOrder(new OrderRequest(sku, quantity, city, cardId));
        }

        /** Only step A runs, then the "application dies". Status on disk: PENDING_B. */
        String paidWithButCrashAfterStepA(String cardId) {
            return restartApp().startOrder(new OrderRequest(sku, quantity, city, cardId)).getOrderId();
        }

        /** Steps A and B run, then the "application dies". Status on disk: PENDING_C. */
        String paidWithButCrashAfterStepB(String cardId) {
            String orderId = paidWithButCrashAfterStepA(cardId);
            new WarehousePicker(dataDir).pickAndPack(orderId, sku, quantity);
            WorkflowStore workflow = new WorkflowStore(dataDir);
            OrderRecord order = workflow.findOrder(orderId).get();
            order.moveTo(OrderRecord.Status.PENDING_C, "B: picked and packed");
            workflow.save(order);
            return orderId;
        }
    }

    // ---------- then (always read from disk) ----------

    void assertStockOf(String sku, int expected) {
        assertEquals(expected, new InventoryManager(dataDir).stockLevels().get(sku), "stock of " + sku);
    }

    void assertOrderIs(String orderId, OrderRecord.Status expected) {
        assertEquals(expected, new WorkflowStore(dataDir).findOrder(orderId).get().getStatus(), "workflow status");
    }

    void assertReservationIs(String orderId, StockReservation.Status expected) {
        assertEquals(expected, new InventoryManager(dataDir).findReservation(orderId).get().getStatus(), "A status");
    }

    void assertPickTaskIs(String orderId, PickTask.Status expected) {
        assertEquals(expected, new WarehousePicker(dataDir).findTask(orderId).get().getStatus(), "B status");
    }

    void assertNoPickTask(String orderId) {
        assertTrue(new WarehousePicker(dataDir).findTask(orderId).isEmpty(), "B must have no task");
    }

    void assertNoShipment(String orderId) {
        assertTrue(new ShippingCarrierApi(dataDir).findShipment(orderId).isEmpty(), "C must have no shipment");
    }

    void assertBalanceOf(String cardId, int expected) {
        assertEquals(expected, new ShippingCarrierApi(dataDir).cardBalances().get(cardId), "balance of " + cardId);
    }
}
