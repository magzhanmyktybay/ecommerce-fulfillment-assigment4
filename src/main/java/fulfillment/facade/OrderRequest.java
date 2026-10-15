package fulfillment.facade;

/** What the client wants: quantity x sku, delivered to city, paid with cardId. */
public record OrderRequest(String sku, int quantity, String city, String cardId) {
}
