package dev.ucomprotocol.spi.mock;

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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory mock implementation of {@link CommerceAdapter} for local
 * development and testing.
 */
public class MockCommerceAdapter implements CommerceAdapter {

    private final CatalogAdapter catalogAdapter = new CatalogAdapter() {
        @Override
        public Optional<Product> getProductById(String id) {
            return Optional.of(Product.builder()
                    .id(id)
                    .name("Mock Product")
                    .price(BigDecimal.TEN)
                    .currency("USD")
                    .build());
        }

        @Override
        public List<Product> searchProducts(String query) {
            return Collections.emptyList();
        }

        @Override
        public List<Product> getProductsByCategory(String categoryId) {
            return Collections.emptyList();
        }
    };

    private final CartAdapter cartAdapter = new CartAdapter() {
        @Override
        public Cart getCart(String cartId) {
            return Cart.builder().id(cartId).build();
        }

        @Override
        public Cart createCart(String customerId) {
            return Cart.builder().id("mock-cart-" + System.currentTimeMillis()).customerId(customerId).build();
        }

        @Override
        public Cart addToCart(String cartId, String productId, int quantity) {
            return Cart.builder().id(cartId).build();
        }

        @Override
        public Cart removeFromCart(String cartId, String itemId) {
            return Cart.builder().id(cartId).build();
        }

        @Override
        public Cart updateItemQuantity(String cartId, String itemId, int quantity) {
            return Cart.builder().id(cartId).build();
        }
    };

    private final Map<String, Checkout> checkouts = new ConcurrentHashMap<>();

    private final CheckoutAdapter checkoutAdapter = new CheckoutAdapter() {
        @Override
        public Checkout createCheckout(Cart cart) {
            Checkout created = Checkout.builder()
                    .id("mock-checkout-" + System.nanoTime())
                    .customerId(cart != null ? cart.getCustomerId() : null)
                    .currency(cart != null && cart.getCurrency() != null ? cart.getCurrency() : "USD")
                    .items(cart != null && cart.getItems() != null ? cart.getItems() : Collections.emptyList())
                    .totalAmount(cart != null ? cart.getTotalAmount() : BigDecimal.ZERO)
                    .status(Checkout.CheckoutStatus.READY_FOR_COMPLETE)
                    .build();
            checkouts.put(created.getId(), created);
            return created;
        }

        @Override
        public Optional<Checkout> getCheckout(String checkoutId) {
            Checkout existing = checkouts.get(checkoutId);
            if (existing != null) {
                return Optional.of(existing);
            }
            return Optional.of(Checkout.builder()
                    .id(checkoutId)
                    .currency("USD")
                    .status(Checkout.CheckoutStatus.READY_FOR_COMPLETE)
                    .build());
        }

        @Override
        public Checkout updateCheckout(String checkoutId, Checkout checkout) {
            Checkout updated = Checkout.builder()
                    .id(checkoutId)
                    .customerId(checkout != null ? checkout.getCustomerId() : null)
                    .currency(checkout != null && checkout.getCurrency() != null ? checkout.getCurrency() : "USD")
                    .items(checkout != null && checkout.getItems() != null ? checkout.getItems() : Collections.emptyList())
                    .totalAmount(checkout != null ? checkout.getTotalAmount() : BigDecimal.ZERO)
                    .status(checkout != null && checkout.getStatus() != null
                            ? checkout.getStatus()
                            : Checkout.CheckoutStatus.READY_FOR_COMPLETE)
                    .build();
            checkouts.put(checkoutId, updated);
            return updated;
        }

        @Override
        public Checkout completeCheckout(String checkoutId) {
            Checkout existing = checkouts.get(checkoutId);
            Checkout completed = Checkout.builder()
                    .id(checkoutId)
                    .customerId(existing != null ? existing.getCustomerId() : null)
                    .currency(existing != null ? existing.getCurrency() : "USD")
                    .items(existing != null ? existing.getItems() : Collections.emptyList())
                    .totalAmount(existing != null ? existing.getTotalAmount() : null)
                    .status(Checkout.CheckoutStatus.COMPLETED)
                    .orderId("mock-order-" + checkoutId)
                    .build();
            checkouts.put(checkoutId, completed);
            return completed;
        }

        @Override
        public Checkout cancelCheckout(String checkoutId) {
            Checkout existing = checkouts.get(checkoutId);
            Checkout canceled = Checkout.builder()
                    .id(checkoutId)
                    .customerId(existing != null ? existing.getCustomerId() : null)
                    .currency(existing != null ? existing.getCurrency() : "USD")
                    .items(existing != null ? existing.getItems() : Collections.emptyList())
                    .totalAmount(existing != null ? existing.getTotalAmount() : null)
                    .status(Checkout.CheckoutStatus.CANCELED)
                    .build();
            checkouts.put(checkoutId, canceled);
            return canceled;
        }
    };

    private final OrderAdapter orderAdapter = new OrderAdapter() {
        @Override
        public Order createOrder(String cartId) {
            return Order.builder().id("mock-order").build();
        }

        @Override
        public Order getOrder(String orderId) {
            return Order.builder().id(orderId).build();
        }

        @Override
        public List<Order> getOrdersByCustomer(String customerId) {
            return Collections.emptyList();
        }

        @Override
        public Order cancelOrder(String orderId) {
            return Order.builder().id(orderId).status(Order.OrderStatus.CANCELLED).build();
        }
    };

    private final CustomerAdapter customerAdapter = new CustomerAdapter() {
        @Override
        public Optional<Customer> getCustomer(String id) {
            return Optional.of(Customer.builder().id(id).firstName("Mock").lastName("User").build());
        }

        @Override
        public Customer createCustomer(Customer customer) {
            return customer;
        }

        @Override
        public Customer updateCustomer(String id, Customer customer) {
            return customer;
        }
    };

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
}
