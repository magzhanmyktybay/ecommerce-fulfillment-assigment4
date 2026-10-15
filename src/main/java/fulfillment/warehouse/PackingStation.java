package fulfillment.warehouse;

/**
 * Pure business logic of Subsystem B: decides which box an order needs and packs the task.
 * No file access.
 */
public class PackingStation {

    public String chooseBox(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Cannot pack " + quantity + " items");
        }
        if (quantity <= 2) {
            return "SMALL";
        }
        if (quantity <= 5) {
            return "MEDIUM";
        }
        return "LARGE";
    }

    public void pack(PickTask task) {
        task.pack(chooseBox(task.getQuantity()));
    }
}
