package fulfillment.facade;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists the chain progress (OrderRecord) in workflow_orders.csv.
 * Used only by the facades, never by a subsystem.
 */
public class WorkflowStore {

    private static final String HEADER = "orderId,sku,quantity,city,cardId,itemsTotal,status,message";
    private static final int FIRST_ORDER_NUMBER = 1001;

    private final Path file;

    public WorkflowStore(Path dataDir) {
        this.file = dataDir.resolve("workflow_orders.csv");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data folder " + dataDir, e);
        }
    }

    public String nextOrderId() {
        return "ORD-" + (FIRST_ORDER_NUMBER + findAll().size());
    }

    public Optional<OrderRecord> findOrder(String orderId) {
        for (OrderRecord order : findAll()) {
            if (order.getOrderId().equals(orderId)) {
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    public List<OrderRecord> findAll() {
        List<OrderRecord> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                String[] p = lines.get(i).split(",", -1);
                result.add(new OrderRecord(p[0], p[1], Integer.parseInt(p[2]), p[3], p[4],
                        Integer.parseInt(p[5]), OrderRecord.Status.valueOf(p[6]), p[7]));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /** Inserts a new record or replaces the one with the same order id. */
    public void save(OrderRecord order) {
        Map<String, String> lines = new LinkedHashMap<>();
        for (OrderRecord o : findAll()) {
            lines.put(o.getOrderId(), toLine(o));
        }
        lines.put(order.getOrderId(), toLine(order));

        List<String> all = new ArrayList<>();
        all.add(HEADER);
        all.addAll(lines.values());
        try {
            Files.write(file, all);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private String toLine(OrderRecord o) {
        return clean(o.getOrderId()) + "," + clean(o.getSku()) + "," + o.getQuantity() + "," + clean(o.getCity()) + ","
                + clean(o.getCardId()) + "," + o.getItemsTotal() + "," + o.getStatus() + "," + clean(o.getMessage());
    }

    /** Commas and line breaks would break the CSV format, so they are replaced. */
    private String clean(String text) {
        return text.replace(",", ";").replace("\n", " ").replace("\r", " ");
    }
}
