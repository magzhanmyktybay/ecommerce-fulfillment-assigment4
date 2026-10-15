package fulfillment.shipping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentAndPaymentGatewayTest {

    private final PaymentGateway gateway = new PaymentGateway();

    @Test
    void gatewayApprovesOnlyCoveredPositiveAmounts() {
        assertTrue(gateway.isApproved(100, 100));
        assertFalse(gateway.isApproved(99, 100));
        assertFalse(gateway.isApproved(100, 0));
        assertThrows(ShippingException.class, () -> gateway.authorize("CARD", 10, 100));
    }

    @Test
    void shipmentMustBePaidBeforeShipping() {
        Shipment s = new Shipment("ORD-1", "CARD", "ASTANA", 100);
        assertThrows(IllegalStateException.class, () -> s.ship("TRK"));
        assertFalse(s.isCharged());
    }

    @Test
    void shipmentCannotBePaidTwiceOrCancelledTwice() {
        Shipment s = new Shipment("ORD-1", "CARD", "ASTANA", 100);
        s.markPaid();
        assertTrue(s.isCharged());
        assertThrows(IllegalStateException.class, s::markPaid);
        s.cancel();
        assertThrows(IllegalStateException.class, s::cancel);
        assertTrue(s.toString().contains("CANCELLED"));
    }
}
