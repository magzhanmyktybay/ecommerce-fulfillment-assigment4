package fulfillment.facade;

import fulfillment.inventory.InventoryException;
import fulfillment.inventory.InventoryManager;
import fulfillment.inventory.StockReservation;
import fulfillment.shipping.Shipment;
import fulfillment.shipping.ShippingCarrierApi;
import fulfillment.shipping.ShippingException;
import fulfillment.warehouse.WarehouseException;
import fulfillment.warehouse.WarehousePicker;

import java.nio.file.Path;
import java.util.List;

/**
 * Main facade for the customer workflow. One call, placeOrder(), drives the whole chain:
 *   A: reserve stock  ->  B: pick and pack  ->  C: charge card and ship
 * and compensates the committed steps if B or C fails.
 */
public class OrderFulfillmentFacade {

    private final InventoryManager inventory;
    private final WarehousePicker warehouse;
    private final ShippingCarrierApi shipping;
    private final WorkflowStore workflow;
    private final Compensator compensator;

    public OrderFulfillmentFacade(Path dataDir) {
        this.inventory = new InventoryManager(dataDir);
        this.warehouse = new WarehousePicker(dataDir);
        this.shipping = new ShippingCarrierApi(dataDir);
        this.workflow = new WorkflowStore(dataDir);
        this.compensator = new Compensator(inventory, warehouse, shipping, workflow);
    }

    /** The whole chain A -> B -> C as one operation. */
    public OrderRecord placeOrder(OrderRequest request) {
        OrderRecord order = startOrder(request);
        return resume(order.getOrderId());
    }

    /**
     * Step A only: creates the order and reserves stock. Persists PENDING_B.
     * Throws OrderRejectedException if the order is invalid or stock is missing (nothing committed).
     */
    public OrderRecord startOrder(OrderRequest request) {
        String orderId = workflow.nextOrderId();
        String sku = normalize(request.sku());
        String city = normalize(request.city());
        String cardId = normalize(request.cardId());

        if (city.isEmpty() || cardId.isEmpty()) {
            return reject(orderId, sku, request.quantity(), city, cardId, "Delivery city and card must not be empty");
        }

        StockReservation reservation;
        try {
            reservation = inventory.reserve(orderId, sku, request.quantity());
        } catch (InventoryException e) {
            return reject(orderId, sku, request.quantity(), city, cardId, e.getMessage());
        }

        OrderRecord order = new OrderRecord(orderId, sku, request.quantity(), city, cardId,
                reservation.getTotalPrice(), OrderRecord.Status.PENDING_B, "A: stock reserved");
        workflow.save(order);
        return order;
    }

    /**
     * Continues an order from the status that is persisted on disk.
     * Used by placeOrder() and after a restart for interrupted orders.
     */
    public OrderRecord resume(String orderId) {
        OrderRecord order = workflow.findOrder(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order " + orderId));

        if (order.getStatus() == OrderRecord.Status.PENDING_B) {
            try {
                warehouse.pickAndPack(orderId, order.getSku(), order.getQuantity());
            } catch (WarehouseException e) {
                throw failAndCompensate(orderId, "step B failed: " + e.getMessage());
            }
            order.moveTo(OrderRecord.Status.PENDING_C, "B: picked and packed");
            workflow.save(order);
        }

        if (order.getStatus() == OrderRecord.Status.PENDING_C) {
            Shipment shipment;
            try {
                shipment = shipping.ship(orderId, order.getCardId(), order.getCity(),
                        order.getItemsTotal(), order.getQuantity());
            } catch (ShippingException e) {
                throw failAndCompensate(orderId, "step C failed: " + e.getMessage());
            }
            inventory.markSold(orderId);
            order.moveTo(OrderRecord.Status.COMPLETED, "C: paid " + shipment.getAmount()
                    + " KZT; shipped with tracking " + shipment.getTrackingNumber());
            workflow.save(order);
        }

        // COMPLETED, COMPENSATED or REJECTED: nothing left to do.
        return order;
    }

    private OrderFailedException failAndCompensate(String orderId, String reason) {
        List<String> steps = compensator.compensate(orderId, reason);
        return new OrderFailedException(orderId, reason, steps);
    }

    private OrderRecord reject(String orderId, String sku, int quantity, String city, String cardId, String reason) {
        workflow.save(new OrderRecord(orderId, sku, quantity, city, cardId, 0, OrderRecord.Status.REJECTED, reason));
        throw new OrderRejectedException(orderId, reason);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase();
    }
}
