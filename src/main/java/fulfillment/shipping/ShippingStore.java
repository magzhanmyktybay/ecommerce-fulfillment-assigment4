package fulfillment.shipping;

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
 * Persistence gateway of Subsystem C. Reads and writes two CSV files:
 *  - shipping_shipments.csv  (orderId,cardId,city,amount,trackingNumber,status)
 *  - shipping_cards.csv      (cardId,balance)   test cards of the payment provider
 */
public class ShippingStore {

    private static final String SHIPMENT_HEADER = "orderId,cardId,city,amount,trackingNumber,status";
    private static final String CARD_HEADER = "cardId,balance";

    private final Path shipmentFile;
    private final Path cardFile;

    public ShippingStore(Path dataDir) {
        this.shipmentFile = dataDir.resolve("shipping_shipments.csv");
        this.cardFile = dataDir.resolve("shipping_cards.csv");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data folder " + dataDir, e);
        }
        if (!Files.exists(cardFile)) {
            List<String> cards = new ArrayList<>();
            cards.add("CARD-OK,2000000");   // rich card, payments succeed
            cards.add("CARD-LOW,20000");    // enough only for cheap orders
            cards.add("CARD-EMPTY,0");      // every payment is declined
            writeLines(cardFile, CARD_HEADER, cards);
        }
    }

    // ---------- cards ----------

    public boolean hasCard(String cardId) {
        return readCards().containsKey(cardId);
    }

    public int getBalance(String cardId) {
        Integer balance = readCards().get(cardId);
        if (balance == null) {
            throw new ShippingException("Unknown card: " + cardId);
        }
        return balance;
    }

    /** Adds a card or overwrites its balance. */
    public void saveCard(String cardId, int balance) {
        Map<String, Integer> cards = readCards();
        cards.put(cardId, balance);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cards.entrySet()) {
            lines.add(e.getKey() + "," + e.getValue());
        }
        writeLines(cardFile, CARD_HEADER, lines);
    }

    public Map<String, Integer> getAllBalances() {
        return readCards();
    }

    private Map<String, Integer> readCards() {
        Map<String, Integer> cards = new LinkedHashMap<>();
        for (String line : readLines(cardFile)) {
            String[] p = line.split(",", -1);
            cards.put(p[0], Integer.parseInt(p[1]));
        }
        return cards;
    }

    // ---------- shipments ----------

    public Optional<Shipment> findShipment(String orderId) {
        for (Shipment s : findAllShipments()) {
            if (s.getOrderId().equals(orderId)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public List<Shipment> findAllShipments() {
        List<Shipment> result = new ArrayList<>();
        for (String line : readLines(shipmentFile)) {
            String[] p = line.split(",", -1);
            result.add(new Shipment(p[0], p[1], p[2], Integer.parseInt(p[3]), p[4], Shipment.Status.valueOf(p[5])));
        }
        return result;
    }

    /** Inserts a new shipment or replaces the one with the same order id. */
    public void saveShipment(Shipment shipment) {
        Map<String, String> lines = new LinkedHashMap<>();
        for (Shipment s : findAllShipments()) {
            lines.put(s.getOrderId(), toLine(s));
        }
        lines.put(shipment.getOrderId(), toLine(shipment));
        writeLines(shipmentFile, SHIPMENT_HEADER, new ArrayList<>(lines.values()));
    }

    private String toLine(Shipment s) {
        return s.getOrderId() + "," + s.getCardId() + "," + s.getCity() + "," + s.getAmount() + ","
                + s.getTrackingNumber() + "," + s.getStatus();
    }

    // ---------- file helpers ----------

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
