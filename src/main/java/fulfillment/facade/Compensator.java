package fulfillment.facade;

import fulfillment.inventory.InventoryManager;
import fulfillment.shipping.ShippingCarrierApi;
import fulfillment.warehouse.WarehousePicker;

import java.util.ArrayList;
import java.util.List;

/**
 * Undoes the committed part of the chain, in reverse order (C, then B, then A).
 * Shared by both facades so the rollback rules exist in exactly one place.
 * The decision is based on the order status that is persisted on disk.
 */
class Compensator {

    private final InventoryManager inventory;
    private final WarehousePicker warehouse;
    private final ShippingCarrierApi shipping;
    private final WorkflowStore workflow;

    Compensator(InventoryManager inventory, WarehousePicker warehouse, ShippingCarrierApi shipping, WorkflowStore workflow) {
        this.inventory = inventory;
        this.warehouse = warehouse;
        this.shipping = shipping;
        this.workflow = workflow;
    }

    /** Returns the executed undo steps, in execution order. */
    List<String> compensate(String orderId, String reason) {
        // Always read the status from disk, never trust an in-memory copy.
        OrderRecord order = workflow.findOrder(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order " + orderId));
        if (!order.isInterrupted()) {
            throw new IllegalStateException("Order " + orderId + " is " + order.getStatus() + " - nothing to compensate");
        }

        List<String> steps = new ArrayList<>();

        if (order.getStatus() == OrderRecord.Status.PENDING_C) {
            // A and B are committed. C normally commits nothing when it fails, but if the app
            // crashed right after C succeeded, C must be undone first (refund).
            if (shipping.cancelShipment(orderId)) {
                steps.add("C: shipment cancelled, card " + order.getCardId() + " refunded");
            }
            if (warehouse.cancelTask(orderId)) {
                steps.add("B: pick task cancelled, items put back on the shelf");
            }
        } else {
            // PENDING_B: only A is committed according to the workflow. B itself fails atomically,
            // but after a crash B could have saved its task just before the status was updated.
            if (warehouse.cancelTask(orderId)) {
                steps.add("B: pick task from the interrupted run cancelled");
            }
        }

        inventory.release(orderId);
        steps.add("A: reservation released, " + order.getQuantity() + " x " + order.getSku() + " back in stock");

        order.moveTo(OrderRecord.Status.COMPENSATED, reason);
        workflow.save(order);
        return steps;
    }
}
