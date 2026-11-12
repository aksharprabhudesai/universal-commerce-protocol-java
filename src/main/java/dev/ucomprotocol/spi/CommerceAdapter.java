package dev.ucomprotocol.spi;

/**
 * Main SPI interface for UCP adapters.
 * Implementations should provide access to specific capability adapters.
 */
public interface CommerceAdapter {
    /**
     * Returns the catalog adapter for product operations.
     *
     * @return the catalog adapter, or {@code null} if catalog is unsupported
     */
    CatalogAdapter getCatalogAdapter();

    /**
     * Returns the cart adapter for shopping cart operations.
     *
     * @return the cart adapter, or {@code null} if cart is unsupported
     */
    CartAdapter getCartAdapter();

    /**
     * Returns the checkout adapter for checkout session operations
     * ({@code dev.ucp.shopping.checkout}).
     * <p>
     * Defaults to {@code null} so existing implementations remain backwards
     * compatible when checkout is not implemented.
     *
     * @return the checkout adapter, or {@code null} if checkout is unsupported
     */
    default CheckoutAdapter getCheckoutAdapter() {
        return null;
    }

    /**
     * Returns the order adapter for order management operations.
     *
     * @return the order adapter, or {@code null} if order management is unsupported
     */
    OrderAdapter getOrderAdapter();

    /**
     * Returns the customer adapter for customer management operations.
     *
     * @return the customer adapter, or {@code null} if customer management is
     *         unsupported
     */
    CustomerAdapter getCustomerAdapter();
}
