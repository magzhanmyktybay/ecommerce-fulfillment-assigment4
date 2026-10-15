package fulfillment.facade;

import fulfillment.inventory.InventoryManager;
import fulfillment.shipping.ShippingCarrierApi;
import fulfillment.warehouse.WarehousePicker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Second, narrower facade for the administrator: inspect persisted state of all three
 * subsystems, mark warehouse shelf bins as blocked, and manually compensate interrupted orders.
 */
public class FulfillmentAdminFacade {

    private final InventoryManager inventory;
    private final WarehousePicker warehouse;
    private final ShippingCarrierApi shipping;
    private final WorkflowStore workflow;
    private final Compensator compensator;

    public FulfillmentAdminFacade(Path dataDir) {
        this.inventory = new InventoryManager(dataDir);
        this.warehouse = new WarehousePicker(dataDir);
        this.shipping = new ShippingCarrierApi(dataDir);
        this.workflow = new WorkflowStore(dataDir);
        this.compensator = new Compensator(inventory, warehouse, shipping, workflow);
    }

    public List<OrderRecord> listOrders() {
        return workflow.findAll();
    }

    /** Orders whose chain stopped half-way (e.g. the application crashed). */
    public List<OrderRecord> listInterruptedOrders() {
        List<OrderRecord> result = new ArrayList<>();
        for (OrderRecord order : workflow.findAll()) {
            if (order.isInterrupted()) {
                result.add(order);
            }
        }
        return result;
    }

    /** One report with the state of the order in the workflow and in each subsystem. */
    public String inspectOrder(String orderId) {
        OrderRecord order = workflow.findOrder(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order " + orderId));
        return "Workflow : " + order + "\n"
                + "A (stock): " + inventory.findReservation(orderId).map(Object::toString).orElse("no reservation") + "\n"
                + "B (pick) : " + warehouse.findTask(orderId).map(Object::toString).orElse("no pick task") + "\n"
                + "C (ship) : " + shipping.findShipment(orderId).map(Object::toString).orElse("no shipment");
    }

    /** Manual compensation of an interrupted order, instead of resuming it. */
    public List<String> compensateManually(String orderId) {
        return compensator.compensate(orderId, "manually compensated by administrator");
    }

    public Map<String, Integer> stockLevels() {
        return inventory.stockLevels();
    }

    public Map<String, Integer> cardBalances() {
        return shipping.cardBalances();
    }

    public void blockShelfBin(String sku) {
        warehouse.blockShelfBin(sku.trim().toUpperCase());
    }

    public void unblockShelfBin(String sku) {
        warehouse.unblockShelfBin(sku.trim().toUpperCase());
    }

    public Set<String> blockedShelfBins() {
        return warehouse.blockedShelfBins();
    }
}
