package fulfillment.warehouse;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service of Subsystem B (Warehouse Picker). Picks an order's items and sends them through
 * the packing station. Knows nothing about inventory or shipping.
 */
public class WarehousePicker {

    private final WarehouseStore store;
    private final PackingStation packingStation = new PackingStation();

    public WarehousePicker(Path dataDir) {
        this.store = new WarehouseStore(dataDir);
    }

    public PickTask pickAndPack(String orderId, String sku, int quantity) {
        // Calling it again for an already packed order (e.g. a retry after a restart) does not pick twice.
        Optional<PickTask> existing = store.findTask(orderId);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == PickTask.Status.PACKED) {
                return existing.get();
            }
            throw new WarehouseException("Pick task for " + orderId + " is already " + existing.get().getStatus());
        }

        // Realistic B-failure: the shelf bin is blocked (damaged rack, closed aisle), nobody can reach the items.
        if (store.isBlocked(sku)) {
            throw new WarehouseException("Shelf bin for " + sku + " is blocked - picker cannot reach the items");
        }

        PickTask task = new PickTask(orderId, sku, quantity);
        task.pick();
        store.saveTask(task);
        packingStation.pack(task);
        store.saveTask(task);
        return task;
    }

    /**
     * Compensation: opens the box and puts the items back on the shelf.
     * Returns true if a task was cancelled, false if there was nothing to cancel.
     */
    public boolean cancelTask(String orderId) {
        Optional<PickTask> task = store.findTask(orderId);
        if (task.isEmpty() || task.get().getStatus() == PickTask.Status.CANCELLED) {
            return false;
        }
        task.get().cancel();
        store.saveTask(task.get());
        return true;
    }

    public Optional<PickTask> findTask(String orderId) {
        return store.findTask(orderId);
    }

    public List<PickTask> findAllTasks() {
        return store.findAllTasks();
    }

    public void blockShelfBin(String sku) {
        store.blockSku(sku);
    }

    public void unblockShelfBin(String sku) {
        store.unblockSku(sku);
    }

    public Set<String> blockedShelfBins() {
        return store.getBlockedSkus();
    }
}
