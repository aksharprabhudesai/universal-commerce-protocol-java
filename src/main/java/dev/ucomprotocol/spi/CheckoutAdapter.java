package dev.ucomprotocol.spi;

import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;

import java.util.Optional;

/**
 * Adapter interface for checkout session operations
 * ({@code dev.ucp.shopping.checkout}).
 * <p>
 * Implement this interface to connect to a commerce platform's checkout flow.
 */
public interface CheckoutAdapter {
    /**
     * Creates a new checkout session from a cart.
     *
     * @param cart the shopping cart to initiate checkout from
     * @return the newly created checkout session
     */
    Checkout createCheckout(Cart cart);

    /**
     * Retrieves a checkout session by its ID.
     *
     * @param checkoutId the unique identifier of the checkout session
     * @return an {@link Optional} containing the checkout if found, or empty
     *         otherwise
     */
    Optional<Checkout> getCheckout(String checkoutId);

    /**
     * Updates an active checkout session (e.g., line items, buyer details).
     *
     * @param checkoutId the unique identifier of the checkout session
     * @param checkout   the updated checkout state
     * @return the updated checkout session
     */
    Checkout updateCheckout(String checkoutId, Checkout checkout);

    /**
     * Completes a checkout session and finalizes order placement.
     *
     * @param checkoutId the unique identifier of the checkout session
     * @return the completed checkout session
     */
    Checkout completeCheckout(String checkoutId);

    /**
     * Completes a checkout session with optional AP2 cryptographic mandate
     * metadata ({@code dev.ucp.common.payment.ap2_mandate}).
     * <p>
     * By default, delegates to {@link #completeCheckout(String)} when not
     * wrapped by AP2 enforcement.
     *
     * @param checkoutId the unique identifier of the checkout session
     * @param ap2        the AP2 mandate payload submitted by the platform/agent,
     *                   or {@code null}
     * @return the completed checkout session
     */
    default Checkout completeCheckout(String checkoutId, Checkout.Ap2Data ap2) {
        return completeCheckout(checkoutId);
    }

    /**
     * Cancels an active checkout session.
     *
     * @param checkoutId the unique identifier of the checkout session
     * @return the canceled checkout session
     */
    Checkout cancelCheckout(String checkoutId);
}
