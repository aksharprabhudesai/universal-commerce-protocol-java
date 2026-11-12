package dev.ucomprotocol.resilience;

import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;
import dev.ucomprotocol.model.Customer;
import dev.ucomprotocol.model.Order;
import dev.ucomprotocol.model.Product;
import dev.ucomprotocol.spi.CartAdapter;
import dev.ucomprotocol.spi.CatalogAdapter;
import dev.ucomprotocol.spi.CheckoutAdapter;
import dev.ucomprotocol.spi.CommerceAdapter;
import dev.ucomprotocol.spi.CustomerAdapter;
import dev.ucomprotocol.spi.OrderAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Decorator for {@link CommerceAdapter} that wraps all supported capability
 * adapter invocations in a Resilience4j {@link Retry} and
 * {@link CircuitBreaker}.
 * <p>
 * Any capability method on the underlying delegate returning {@code null}
 * continues to return {@code null}, preserving capability discovery semantics.
 */
public class ResilientCommerceAdapter implements CommerceAdapter {

    private final CommerceAdapter delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    private final CatalogAdapter catalogAdapter;
    private final CartAdapter cartAdapter;
    private final CheckoutAdapter checkoutAdapter;
    private final OrderAdapter orderAdapter;
    private final CustomerAdapter customerAdapter;

    /**
     * Creates a resilient decorator around the given {@link CommerceAdapter}.
     *
     * @param delegate       the underlying commerce adapter (must not be null)
     * @param circuitBreaker the circuit breaker instance, or {@code null} to skip
     *                       circuit breaking
     * @param retry          the retry instance, or {@code null} to skip retries
     */
    public ResilientCommerceAdapter(CommerceAdapter delegate, CircuitBreaker circuitBreaker, Retry retry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;

        CatalogAdapter rawCatalog = delegate.getCatalogAdapter();
        this.catalogAdapter = rawCatalog != null ? wrapCatalog(rawCatalog) : null;

        CartAdapter rawCart = delegate.getCartAdapter();
        this.cartAdapter = rawCart != null ? wrapCart(rawCart) : null;

        CheckoutAdapter rawCheckout = delegate.getCheckoutAdapter();
        this.checkoutAdapter = rawCheckout != null ? wrapCheckout(rawCheckout) : null;

        OrderAdapter rawOrder = delegate.getOrderAdapter();
        this.orderAdapter = rawOrder != null ? wrapOrder(rawOrder) : null;

        CustomerAdapter rawCustomer = delegate.getCustomerAdapter();
        this.customerAdapter = rawCustomer != null ? wrapCustomer(rawCustomer) : null;
    }

    /**
     * Returns the underlying unwrapped {@link CommerceAdapter}.
     *
     * @return the delegate adapter
     */
    public CommerceAdapter getDelegate() {
        return delegate;
    }

    @Override
    public CatalogAdapter getCatalogAdapter() {
        return catalogAdapter;
    }

    @Override
    public CartAdapter getCartAdapter() {
        return cartAdapter;
    }

    @Override
    public CheckoutAdapter getCheckoutAdapter() {
        return checkoutAdapter;
    }

    @Override
    public OrderAdapter getOrderAdapter() {
        return orderAdapter;
    }

    @Override
    public CustomerAdapter getCustomerAdapter() {
        return customerAdapter;
    }

    private <T> T execute(Supplier<T> supplier) {
        Supplier<T> decorated = supplier;
        if (circuitBreaker != null) {
            decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
        }
        if (retry != null) {
            decorated = Retry.decorateSupplier(retry, decorated);
        }
        return decorated.get();
    }

    private CatalogAdapter wrapCatalog(CatalogAdapter raw) {
        return new CatalogAdapter() {
            @Override
            public Optional<Product> getProductById(String id) {
                return execute(() -> raw.getProductById(id));
            }

            @Override
            public List<Product> searchProducts(String query) {
                return execute(() -> raw.searchProducts(query));
            }

            @Override
            public List<Product> getProductsByCategory(String categoryId) {
                return execute(() -> raw.getProductsByCategory(categoryId));
            }
        };
    }

    private CartAdapter wrapCart(CartAdapter raw) {
        return new CartAdapter() {
            @Override
            public Cart getCart(String cartId) {
                return execute(() -> raw.getCart(cartId));
            }

            @Override
            public Cart createCart(String customerId) {
                return execute(() -> raw.createCart(customerId));
            }

            @Override
            public Cart addToCart(String cartId, String productId, int quantity) {
                return execute(() -> raw.addToCart(cartId, productId, quantity));
            }

            @Override
            public Cart removeFromCart(String cartId, String itemId) {
                return execute(() -> raw.removeFromCart(cartId, itemId));
            }

            @Override
            public Cart updateItemQuantity(String cartId, String itemId, int quantity) {
                return execute(() -> raw.updateItemQuantity(cartId, itemId, quantity));
            }
        };
    }

    private CheckoutAdapter wrapCheckout(CheckoutAdapter raw) {
        return new CheckoutAdapter() {
            @Override
            public Checkout createCheckout(Cart cart) {
                return execute(() -> raw.createCheckout(cart));
            }

            @Override
            public Optional<Checkout> getCheckout(String checkoutId) {
                return execute(() -> raw.getCheckout(checkoutId));
            }

            @Override
            public Checkout updateCheckout(String checkoutId, Checkout checkout) {
                return execute(() -> raw.updateCheckout(checkoutId, checkout));
            }

            @Override
            public Checkout completeCheckout(String checkoutId) {
                return execute(() -> raw.completeCheckout(checkoutId));
            }

            @Override
            public Checkout cancelCheckout(String checkoutId) {
                return execute(() -> raw.cancelCheckout(checkoutId));
            }
        };
    }

    private OrderAdapter wrapOrder(OrderAdapter raw) {
        return new OrderAdapter() {
            @Override
            public Order createOrder(String cartId) {
                return execute(() -> raw.createOrder(cartId));
            }

            @Override
            public Order getOrder(String orderId) {
                return execute(() -> raw.getOrder(orderId));
            }

            @Override
            public List<Order> getOrdersByCustomer(String customerId) {
                return execute(() -> raw.getOrdersByCustomer(customerId));
            }

            @Override
            public Order cancelOrder(String orderId) {
                return execute(() -> raw.cancelOrder(orderId));
            }
        };
    }

    private CustomerAdapter wrapCustomer(CustomerAdapter raw) {
        return new CustomerAdapter() {
            @Override
            public Optional<Customer> getCustomer(String id) {
                return execute(() -> raw.getCustomer(id));
            }

            @Override
            public Customer createCustomer(Customer customer) {
                return execute(() -> raw.createCustomer(customer));
            }

            @Override
            public Customer updateCustomer(String id, Customer customer) {
                return execute(() -> raw.updateCustomer(id, customer));
            }
        };
    }
}
