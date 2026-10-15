package fulfillment.app;

import fulfillment.facade.FulfillmentAdminFacade;
import fulfillment.facade.OrderFailedException;
import fulfillment.facade.OrderFulfillmentFacade;
import fulfillment.facade.OrderRecord;
import fulfillment.facade.OrderRejectedException;
import fulfillment.facade.OrderRequest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Console demo. The client only knows the two facades, never the subsystems.
 * Data folder: ./data (or the first program argument).
 */
public class Main {

    private final OrderFulfillmentFacade orders;
    private final FulfillmentAdminFacade admin;
    private final Scanner in = new Scanner(System.in);

    public Main(Path dataDir) {
        this.orders = new OrderFulfillmentFacade(dataDir);
        this.admin = new FulfillmentAdminFacade(dataDir);
    }

    public static void main(String[] args) {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");
        System.out.println("=== E-Commerce Fulfillment demo ===");
        System.out.println("Data folder: " + dataDir.toAbsolutePath());
        new Main(dataDir).run();
    }

    private void run() {
        warnAboutInterruptedOrders();
        while (true) {
            printMenu();
            String choice = readLine("Choose");
            try {
                switch (choice) {
                    case "1" -> placeOrder(false);
                    case "2" -> printStock();
                    case "3" -> printOrders();
                    case "4" -> System.out.println(admin.inspectOrder(readLine("Order id").toUpperCase()));
                    case "5" -> printCards();
                    case "6" -> blockShelf();
                    case "7" -> unblockShelf();
                    case "8" -> placeOrder(true);
                    case "9" -> resumeOrder();
                    case "10" -> compensateOrder();
                    case "0" -> {
                        System.out.println("Bye.");
                        return;
                    }
                    default -> System.out.println("Unknown option.");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println(" 1. Place an order (full A -> B -> C chain)");
        System.out.println(" 2. Show stock levels                    (Subsystem A)");
        System.out.println(" 3. Show all orders                      (workflow status)");
        System.out.println(" 4. Inspect one order in A, B and C");
        System.out.println(" 5. Show card balances                   (Subsystem C)");
        System.out.println(" 6. Block a shelf bin                    -> makes step B fail");
        System.out.println(" 7. Unblock a shelf bin");
        System.out.println(" 8. Start an order and CRASH after step A (app exits)");
        System.out.println(" 9. Resume an interrupted order");
        System.out.println("10. Manually compensate an interrupted order");
        System.out.println(" 0. Exit");
        System.out.println("Tip: card CARD-EMPTY or city MOON makes step C fail.");
    }

    private void placeOrder(boolean crashAfterStepA) {
        String sku = readLine("Product (e.g. LAPTOP, PHONE, HEADPHONES, KEYBOARD, MOUSE)");
        int quantity = readInt("Quantity");
        String city = readLine("Delivery city (ASTANA, ALMATY, SHYMKENT, KARAGANDA)");
        String card = readLine("Card (CARD-OK, CARD-LOW, CARD-EMPTY)");
        OrderRequest request = new OrderRequest(sku, quantity, city, card);
        try {
            if (crashAfterStepA) {
                OrderRecord order = orders.startOrder(request);
                System.out.println("Step A committed: " + order);
                System.out.println("Simulating a crash now. Restart the app and use option 9 or 10.");
                System.exit(1);
            }
            OrderRecord order = orders.placeOrder(request);
            System.out.println("SUCCESS: " + order);
        } catch (OrderRejectedException e) {
            System.out.println("REJECTED (nothing was committed): " + e.getMessage());
        } catch (OrderFailedException e) {
            printFailure(e);
        }
    }

    private void resumeOrder() {
        String orderId = readLine("Order id").toUpperCase();
        try {
            System.out.println("Result: " + orders.resume(orderId));
        } catch (OrderFailedException e) {
            printFailure(e);
        }
    }

    private void compensateOrder() {
        String orderId = readLine("Order id").toUpperCase();
        List<String> steps = admin.compensateManually(orderId);
        System.out.println("Compensated " + orderId + ":");
        steps.forEach(step -> System.out.println("  undo -> " + step));
    }

    private void printFailure(OrderFailedException e) {
        System.out.println("FAILED: " + e.getMessage());
        System.out.println("Compensation executed (in this order):");
        e.getCompensationSteps().forEach(step -> System.out.println("  undo -> " + step));
    }

    private void printStock() {
        System.out.println("Stock:");
        printMap(admin.stockLevels(), "pcs");
        System.out.println("Blocked shelf bins: " + admin.blockedShelfBins());
    }

    private void printCards() {
        System.out.println("Card balances:");
        printMap(admin.cardBalances(), "KZT");
    }

    private void printOrders() {
        List<OrderRecord> all = admin.listOrders();
        if (all.isEmpty()) {
            System.out.println("No orders yet.");
        }
        all.forEach(System.out::println);
    }

    private void blockShelf() {
        String sku = readLine("Product whose shelf bin is blocked");
        admin.blockShelfBin(sku);
        System.out.println("Blocked shelf bins: " + admin.blockedShelfBins());
    }

    private void unblockShelf() {
        String sku = readLine("Product whose shelf bin is free again");
        admin.unblockShelfBin(sku);
        System.out.println("Blocked shelf bins: " + admin.blockedShelfBins());
    }

    private void warnAboutInterruptedOrders() {
        List<OrderRecord> interrupted = admin.listInterruptedOrders();
        if (!interrupted.isEmpty()) {
            System.out.println("WARNING: interrupted orders found on disk (resume with 9 or compensate with 10):");
            interrupted.forEach(o -> System.out.println("  " + o));
        }
    }

    private void printMap(Map<String, Integer> map, String unit) {
        map.forEach((key, value) -> System.out.println("  " + key + ": " + value + " " + unit));
    }

    private String readLine(String prompt) {
        System.out.print(prompt + ": ");
        if (!in.hasNextLine()) {
            System.exit(0);   // input closed (e.g. Ctrl+D)
        }
        return in.nextLine().trim();
    }

    private int readInt(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }
}
