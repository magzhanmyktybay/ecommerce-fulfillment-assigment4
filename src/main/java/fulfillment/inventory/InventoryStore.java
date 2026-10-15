package fulfillment.inventory;

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
 * Persistence gateway of Subsystem A. Reads and writes two CSV files:
 *  - inventory_stock.csv        (sku,unitPrice,quantity)
 *  - inventory_reservations.csv (orderId,sku,quantity,totalPrice,status)
 * Every method reads from / writes to disk, nothing is cached in memory.
 */
public class InventoryStore {

    private static final String STOCK_HEADER = "sku,unitPrice,quantity";
    private static final String RESERVATION_HEADER = "orderId,sku,quantity,totalPrice,status";

    private final Path stockFile;
    private final Path reservationFile;

    public InventoryStore(Path dataDir) {
        this.stockFile = dataDir.resolve("inventory_stock.csv");
        this.reservationFile = dataDir.resolve("inventory_reservations.csv");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data folder " + dataDir, e);
        }
        if (!Files.exists(stockFile)) {
            seedDefaultProducts();
        }
    }

    private void seedDefaultProducts() {
        List<String> lines = new ArrayList<>();
        lines.add("LAPTOP,350000,5");
        lines.add("PHONE,180000,8");
        lines.add("HEADPHONES,45000,10");
        lines.add("KEYBOARD,15000,15");
        lines.add("MOUSE,8000,20");
        writeLines(stockFile, STOCK_HEADER, lines);
    }

    // ---------- stock ----------

    public boolean hasProduct(String sku) {
        return findStockRow(sku) != null;
    }

    public int getUnitPrice(String sku) {
        return Integer.parseInt(requireStockRow(sku)[1]);
    }

    public int getQuantity(String sku) {
        return Integer.parseInt(requireStockRow(sku)[2]);
    }

    /** Adds a new product or overwrites price and quantity of an existing one. */
    public void saveProduct(String sku, int unitPrice, int quantity) {
        Map<String, String[]> rows = readStock();
        rows.put(sku, new String[]{sku, String.valueOf(unitPrice), String.valueOf(quantity)});
        writeStock(rows);
    }

    public void setQuantity(String sku, int quantity) {
        saveProduct(sku, getUnitPrice(sku), quantity);
    }

    /** sku -> quantity, in file order. */
    public Map<String, Integer> getAllQuantities() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String[] row : readStock().values()) {
            result.put(row[0], Integer.parseInt(row[2]));
        }
        return result;
    }

    // ---------- reservations ----------

    public Optional<StockReservation> findReservation(String orderId) {
        for (StockReservation r : findAllReservations()) {
            if (r.getOrderId().equals(orderId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public List<StockReservation> findAllReservations() {
        List<StockReservation> result = new ArrayList<>();
        for (String[] row : readRows(reservationFile)) {
            result.add(new StockReservation(row[0], row[1], Integer.parseInt(row[2]),
                    Integer.parseInt(row[3]), StockReservation.Status.valueOf(row[4])));
        }
        return result;
    }

    /** Inserts a new reservation or replaces the one with the same order id. */
    public void saveReservation(StockReservation reservation) {
        Map<String, String> lines = new LinkedHashMap<>();
        for (StockReservation r : findAllReservations()) {
            lines.put(r.getOrderId(), toLine(r));
        }
        lines.put(reservation.getOrderId(), toLine(reservation));
        writeLines(reservationFile, RESERVATION_HEADER, new ArrayList<>(lines.values()));
    }

    private String toLine(StockReservation r) {
        return r.getOrderId() + "," + r.getSku() + "," + r.getQuantity() + "," + r.getTotalPrice() + "," + r.getStatus();
    }

    // ---------- CSV helpers ----------

    private Map<String, String[]> readStock() {
        Map<String, String[]> rows = new LinkedHashMap<>();
        for (String[] row : readRows(stockFile)) {
            rows.put(row[0], row);
        }
        return rows;
    }

    private void writeStock(Map<String, String[]> rows) {
        List<String> lines = new ArrayList<>();
        for (String[] row : rows.values()) {
            lines.add(String.join(",", row));
        }
        writeLines(stockFile, STOCK_HEADER, lines);
    }

    private String[] findStockRow(String sku) {
        return readStock().get(sku);
    }

    private String[] requireStockRow(String sku) {
        String[] row = findStockRow(sku);
        if (row == null) {
            throw new InventoryException("Unknown product: " + sku);
        }
        return row;
    }

    private List<String[]> readRows(Path file) {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(file)) {
            return rows;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 1; i < lines.size(); i++) {          // line 0 is the header
                if (!lines.get(i).isBlank()) {
                    rows.add(lines.get(i).split(",", -1));
                }
            }
            return rows;
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
