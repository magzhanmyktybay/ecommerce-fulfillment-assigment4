package fulfillment.warehouse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence gateway of Subsystem B. Reads and writes two CSV files:
 *  - warehouse_tasks.csv          (orderId,sku,quantity,boxSize,status)
 *  - warehouse_blocked_bins.csv   (sku)  shelf bins the picker cannot reach
 */
public class WarehouseStore {

    private static final String TASK_HEADER = "orderId,sku,quantity,boxSize,status";
    private static final String BLOCKED_HEADER = "sku";

    private final Path taskFile;
    private final Path blockedFile;

    public WarehouseStore(Path dataDir) {
        this.taskFile = dataDir.resolve("warehouse_tasks.csv");
        this.blockedFile = dataDir.resolve("warehouse_blocked_bins.csv");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data folder " + dataDir, e);
        }
    }

    // ---------- pick tasks ----------

    public Optional<PickTask> findTask(String orderId) {
        for (PickTask task : findAllTasks()) {
            if (task.getOrderId().equals(orderId)) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    public List<PickTask> findAllTasks() {
        List<PickTask> result = new ArrayList<>();
        for (String line : readLines(taskFile)) {
            String[] p = line.split(",", -1);
            result.add(new PickTask(p[0], p[1], Integer.parseInt(p[2]), p[3], PickTask.Status.valueOf(p[4])));
        }
        return result;
    }

    /** Inserts a new task or replaces the one with the same order id. */
    public void saveTask(PickTask task) {
        Map<String, String> lines = new LinkedHashMap<>();
        for (PickTask t : findAllTasks()) {
            lines.put(t.getOrderId(), toLine(t));
        }
        lines.put(task.getOrderId(), toLine(task));
        writeLines(taskFile, TASK_HEADER, new ArrayList<>(lines.values()));
    }

    private String toLine(PickTask t) {
        return t.getOrderId() + "," + t.getSku() + "," + t.getQuantity() + "," + t.getBoxSize() + "," + t.getStatus();
    }

    // ---------- blocked shelf bins ----------

    public Set<String> getBlockedSkus() {
        return new LinkedHashSet<>(readLines(blockedFile));
    }

    public boolean isBlocked(String sku) {
        return getBlockedSkus().contains(sku);
    }

    public void blockSku(String sku) {
        Set<String> blocked = getBlockedSkus();
        blocked.add(sku);
        writeLines(blockedFile, BLOCKED_HEADER, new ArrayList<>(blocked));
    }

    public void unblockSku(String sku) {
        Set<String> blocked = getBlockedSkus();
        blocked.remove(sku);
        writeLines(blockedFile, BLOCKED_HEADER, new ArrayList<>(blocked));
    }

    // ---------- file helpers ----------

    /** Returns all non-empty lines except the header. */
    private List<String> readLines(Path file) {
        List<String> result = new ArrayList<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 1; i < lines.size(); i++) {
                if (!lines.get(i).isBlank()) {
                    result.add(lines.get(i));
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private void writeLines(Path file, String header, List<String> lines) {
        List<String> all = new ArrayList<>();
        all.add(header);
        all.addAll(lines);
        try {
            Files.write(file, all);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
