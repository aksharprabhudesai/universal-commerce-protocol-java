package dev.ucomprotocol.ap2;

import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;
import dev.ucomprotocol.spi.CartAdapter;
import dev.ucomprotocol.spi.CatalogAdapter;
import dev.ucomprotocol.spi.CheckoutAdapter;
import dev.ucomprotocol.spi.CommerceAdapter;
import dev.ucomprotocol.spi.CustomerAdapter;
import dev.ucomprotocol.spi.OrderAdapter;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorator for {@link CommerceAdapter} that automatically applies the UCP
 * {@code 2026-08-25} AP2 Mandates extension
 * ({@code dev.ucp.common.payment.ap2_mandate}) to checkout sessions.
 * <p>
 * When active, every {@link Checkout} returned from {@code createCheckout},
 * {@code getCheckout}, {@code updateCheckout}, {@code completeCheckout}, and
 * {@code cancelCheckout} carries a fresh detached JWS
 * {@code ap2.merchant_authorization} signature, and {@code completeCheckout}
 * enforces {@code ap2.checkout_mandate} verification prior to order placement.
 */
public class Ap2CommerceAdapter implements CommerceAdapter {

    private final CommerceAdapter delegate;
    private final Ap2MandateService ap2MandateService;
    private final CheckoutAdapter checkoutAdapter;

    /**
     * Creates an AP2-enforcing decorator around the given {@link CommerceAdapter}.
     *
     * @param delegate          underlying commerce adapter
     * @param ap2MandateService cryptographic AP2 signing and verification service
     */
    public Ap2CommerceAdapter(CommerceAdapter delegate, Ap2MandateService ap2MandateService) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.ap2MandateService = Objects.requireNonNull(ap2MandateService, "ap2MandateService must not be null");
        CheckoutAdapter rawCheckout = delegate.getCheckoutAdapter();
        this.checkoutAdapter = rawCheckout != null ? wrapCheckout(rawCheckout) : null;
    }

    /**
     * Returns the underlying wrapped {@link CommerceAdapter}.
     *
     * @return the delegate adapter
     */
    public CommerceAdapter getDelegate() {
        return delegate;
    }

    @Override
    public CatalogAdapter getCatalogAdapter() {
        return delegate.getCatalogAdapter();
    }

    @Override
    public CartAdapter getCartAdapter() {
        return delegate.getCartAdapter();
    }

    @Override
    public CheckoutAdapter getCheckoutAdapter() {
        return checkoutAdapter;
    }

    @Override
    public OrderAdapter getOrderAdapter() {
        return delegate.getOrderAdapter();
    }

    @Override
    public CustomerAdapter getCustomerAdapter() {
        return delegate.getCustomerAdapter();
    }

    private CheckoutAdapter wrapCheckout(CheckoutAdapter raw) {
        return new CheckoutAdapter() {
            @Override
            public Checkout createCheckout(Cart cart) {
                return signCopy(raw.createCheckout(cart), null);
            }

            @Override
            public Optional<Checkout> getCheckout(String checkoutId) {
                return raw.getCheckout(checkoutId).map(chk -> signCopy(chk, null));
            }

            @Override
            public Checkout updateCheckout(String checkoutId, Checkout checkout) {
                return signCopy(raw.updateCheckout(checkoutId, checkout), null);
            }

            @Override
            public Checkout completeCheckout(String checkoutId) {
                return completeCheckout(checkoutId, null);
            }

            @Override
            public Checkout completeCheckout(String checkoutId, Checkout.Ap2Data ap2) {
                Checkout active = raw.getCheckout(checkoutId)
                        .orElseThrow(() -> new IllegalArgumentException("Checkout not found: " + checkoutId));
                ap2MandateService.verifyCheckoutMandate(active, ap2);
                Checkout completed = raw.completeCheckout(checkoutId, ap2);
                String verifiedMandate = ap2 != null ? ap2.getCheckoutMandate() : null;
                return signCopy(completed, verifiedMandate);
            }

            @Override
            public Checkout cancelCheckout(String checkoutId) {
                return signCopy(raw.cancelCheckout(checkoutId), null);
            }
        };
    }

    private Checkout signCopy(Checkout source, String checkoutMandate) {
        if (source == null) {
            return null;
        }
        String mandateToAttach = checkoutMandate != null
                ? checkoutMandate
                : (source.getAp2() != null ? source.getAp2().getCheckoutMandate() : null);

        Checkout copy = Checkout.builder()
                .id(source.getId())
                .customerId(source.getCustomerId())
                .currency(source.getCurrency())
                .status(source.getStatus())
                .items(source.getItems() != null ? new ArrayList<>(source.getItems()) : null)
                .totalAmount(source.getTotalAmount())
                .continueUrl(source.getContinueUrl())
                .expiresAt(source.getExpiresAt())
                .orderId(source.getOrderId())
                .ap2(mandateToAttach != null
                        ? Checkout.Ap2Data.builder().checkoutMandate(mandateToAttach).build()
                        : null)
                .build();
        return ap2MandateService.signCheckout(copy);
    }
}
