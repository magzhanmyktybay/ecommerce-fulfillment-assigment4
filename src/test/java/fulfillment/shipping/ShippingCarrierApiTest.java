package fulfillment.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem C used directly, without any facade. */
class ShippingCarrierApiTest {

    @TempDir
    Path dataDir;

    private ShippingCarrierApi carrier;

    @BeforeEach
    void setUp() {
        new ShippingStore(dataDir).saveCard("CARD-T", 50000);
        carrier = new ShippingCarrierApi(dataDir);
    }

    @Test
    void shipChargesTheCardAndPersistsAShippedParcel() {
        Shipment s = carrier.ship("ORD-1", "CARD-T", "ALMATY", 16000, 2);

        assertEquals(Shipment.Status.SHIPPED, s.getStatus());
        assertEquals(16000 + 2500, s.getAmount());
        assertEquals("TRK-ORD-1", s.getTrackingNumber());
        ShippingCarrierApi afterRestart = new ShippingCarrierApi(dataDir);
        assertEquals(50000 - 18500, afterRestart.cardBalances().get("CARD-T"));
        assertEquals(Shipment.Status.SHIPPED, afterRestart.findShipment("ORD-1").get().getStatus());
    }

    @Test
    void shippingTheSameOrderTwiceChargesOnlyOnce() {
        carrier.ship("ORD-1", "CARD-T", "ALMATY", 8000, 1);
        carrier.ship("ORD-1", "CARD-T", "ALMATY", 8000, 1);

        assertEquals(50000 - 10000, carrier.cardBalances().get("CARD-T"));
        assertEquals(1, carrier.findAllShipments().size());
    }

    @Test
    void cityOutsideTheCarrierNetworkIsRefusedWithoutCharging() {
        assertThrows(ShippingException.class, () -> carrier.ship("ORD-1", "CARD-T", "MOON", 8000, 1));
        assertEquals(50000, carrier.cardBalances().get("CARD-T"));
        assertTrue(carrier.findShipment("ORD-1").isEmpty());
    }

    @Test
    void declinedPaymentLeavesNoShipment() {
        ShippingException e = assertThrows(ShippingException.class,
                () -> carrier.ship("ORD-1", "CARD-T", "ASTANA", 100000, 1));
        assertTrue(e.getMessage().contains("declined"));
        assertTrue(carrier.findShipment("ORD-1").isEmpty());
    }

    @Test
    void unknownCardIsRefused() {
        assertThrows(ShippingException.class, () -> carrier.ship("ORD-1", "CARD-X", "ASTANA", 100, 1));
        assertThrows(ShippingException.class, () -> new ShippingStore(dataDir).getBalance("CARD-X"));
    }

    @Test
    void cancelShipmentRefundsTheCardOnce() {
        carrier.ship("ORD-1", "CARD-T", "ASTANA", 8000, 1);

        assertTrue(carrier.cancelShipment("ORD-1"));
        assertFalse(carrier.cancelShipment("ORD-1"));
        assertEquals(50000, carrier.cardBalances().get("CARD-T"));
        assertEquals(Shipment.Status.CANCELLED, carrier.findShipment("ORD-1").get().getStatus());
    }

    @Test
    void cancellingAnUnknownShipmentDoesNothing() {
        assertFalse(carrier.cancelShipment("ORD-404"));
    }

    @Test
    void storeSeedsDemoCardsInAnEmptyFolder(@TempDir Path emptyDir) {
        ShippingStore store = new ShippingStore(emptyDir);
        assertEquals(0, store.getBalance("CARD-EMPTY"));
        assertTrue(store.hasCard("CARD-OK"));
    }
}
