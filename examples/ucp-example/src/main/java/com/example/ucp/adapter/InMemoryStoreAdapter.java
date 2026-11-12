package com.example.ucp.adapter;

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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stateful in-memory {@link CommerceAdapter} implementing all UCP shopping and
 * identity capabilities (catalog, cart, checkout, order, customer) for testing
 * with AI agents and HTTP clients.
 */
@Component
public class InMemoryStoreAdapter implements CommerceAdapter {

    private static final String DEFAULT_CURRENCY = "USD";

    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();
    private final Map<String, Checkout> checkouts = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Customer> customers = new ConcurrentHashMap<>();

    private final AtomicLong cartSeq = new AtomicLong(1000);
    private final AtomicLong itemSeq = new AtomicLong(5000);
    private final AtomicLong checkoutSeq = new AtomicLong(2000);
    private final AtomicLong orderSeq = new AtomicLong(3000);
    private final AtomicLong customerSeq = new AtomicLong(4000);

    private final CatalogAdapter catalogAdapter = new StoreCatalogAdapter();
    private final CartAdapter cartAdapter = new StoreCartAdapter();
    private final CheckoutAdapter checkoutAdapter = new StoreCheckoutAdapter();
    private final OrderAdapter orderAdapter = new StoreOrderAdapter();
    private final CustomerAdapter customerAdapter = new StoreCustomerAdapter();

    public InMemoryStoreAdapter() {
        seedCatalog();
        seedCustomers();
    }

    private void seedCatalog() {
        registerProduct(Product.builder()
                .id("prod-espresso-01")
                .sku("ESP-PRO-01")
                .name("Barista Pro Espresso Machine")
                .description("Dual-boiler stainless steel espresso machine with PID temperature control.")
                .price(new BigDecimal("499.00"))
                .currency(DEFAULT_CURRENCY)
                .imageUrl("https://example.com/images/espresso-pro.jpg")
                .attributes(Map.of("category", "coffee", "brand", "CremaCraft", "inStock", true))
                .build());

        registerProduct(Product.builder()
                .id("prod-grinder-02")
                .sku("GRD-BURR-02")
                .name("Conical Burr Coffee Grinder")
                .description("40-step precision burr grinder for espresso, pour-over, and French press.")
                .price(new BigDecimal("149.00"))
                .currency(DEFAULT_CURRENCY)
                .imageUrl("https://example.com/images/burr-grinder.jpg")
                .attributes(Map.of("category", "coffee", "brand", "CremaCraft", "inStock", true))
                .build());

        registerProduct(Product.builder()
                .id("prod-mugs-03")
                .sku("MUG-DBL-03")
                .name("Double-Wall Ceramic Espresso Cups (Set of 2)")
                .description("Hand-glazed thermal ceramic cups that keep espresso warm without burning fingers.")
                .price(new BigDecimal("34.00"))
                .currency(DEFAULT_CURRENCY)
                .imageUrl("https://example.com/images/espresso-cups.jpg")
                .attributes(Map.of("category", "accessories", "brand", "KilnWorks", "inStock", true))
                .build());

        registerProduct(Product.builder()
                .id("prod-kettle-04")
                .sku("KTL-GNK-04")
                .name("Precision Gooseneck Pour-Over Kettle")
                .description("Variable temperature electric gooseneck kettle with built-in brew stopwatch.")
                .price(new BigDecimal("89.00"))
                .currency(DEFAULT_CURRENCY)
                .imageUrl("https://example.com/images/gooseneck-kettle.jpg")
                .attributes(Map.of("category", "coffee", "brand", "CremaCraft", "inStock", true))
                .build());

        registerProduct(Product.builder()
                .id("prod-scale-05")
                .sku("SCL-DIG-05")
                .name("Smart Digital Coffee Scale")
                .description("0.1g accuracy rechargeable scale with auto-tare and flow-rate display.")
                .price(new BigDecimal("59.00"))
                .currency(DEFAULT_CURRENCY)
                .imageUrl("https://example.com/images/coffee-scale.jpg")
                .attributes(Map.of("category", "electronics", "brand", "BrewMetrics", "inStock", true))
                .build());

        registerProduct(Product.builder()
                .id("prod-keyboard-06")
                .sku("KBD-MECH-06")
                .name("Wireless Mechanical Developer Keyboard")
                .description("Hot-swappable 75% tactile mechanical keyboard with multi-device Bluetooth and USB-C.")
                .price(new BigDecimal("179.00"))
                .currency(DEFAULT_CURRENCY)
                .imageUrl("https://example.com/images/mech-keyboard.jpg")
                .attributes(Map.of("category", "electronics", "brand", "KeyForge", "inStock", true))
                .build());
    }

    private void seedCustomers() {
        Customer demo = Customer.builder()
                .id("cust-demo-01")
                .externalId("ext-demo-01")
                .email("alex@example.com")
                .firstName("Alex")
                .lastName("Rivera")
                .phoneNumber("+1-555-0100")
                .build();
        customers.put(demo.getId(), demo);
    }

    private void registerProduct(Product product) {
        products.put(product.getId(), product);
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

    private static BigDecimal calculateTotal(List<Cart.CartItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return items.stream()
                .map(Cart.CartItem::getTotalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static Cart copyCart(Cart source) {
        List<Cart.CartItem> copiedItems = new ArrayList<>();
        if (source.getItems() != null) {
            for (Cart.CartItem item : source.getItems()) {
                copiedItems.add(Cart.CartItem.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .name(item.getName())
                        .sku(item.getSku())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .imageUrl(item.getImageUrl())
                        .build());
            }
        }
        return Cart.builder()
                .id(source.getId())
                .customerId(source.getCustomerId())
                .currency(source.getCurrency())
                .items(copiedItems)
                .totalAmount(calculateTotal(copiedItems))
                .build();
    }

    private class StoreCatalogAdapter implements CatalogAdapter {
        @Override
        public Optional<Product> getProductById(String id) {
            if (id == null) {
                return Optional.empty();
            }
            Product direct = products.get(id);
            if (direct != null) {
                return Optional.of(direct);
            }
            return products.values().stream()
                    .filter(p -> id.equalsIgnoreCase(p.getSku()))
                    .findFirst();
        }

        @Override
        public List<Product> searchProducts(String query) {
            if (query == null || query.isBlank()) {
                return List.copyOf(products.values());
            }
            String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
            return products.values().stream()
                    .filter(p -> matchesAllTerms(p, terms))
                    .toList();
        }

        @Override
        public List<Product> getProductsByCategory(String categoryId) {
            if (categoryId == null || categoryId.isBlank()) {
                return List.of();
            }
            return products.values().stream()
                    .filter(p -> p.getAttributes() != null
                            && categoryId.equalsIgnoreCase(String.valueOf(p.getAttributes().get("category"))))
                    .toList();
        }

        private boolean matchesAllTerms(Product p, String[] terms) {
            String haystack = String.join(" ",
                    p.getId() != null ? p.getId() : "",
                    p.getSku() != null ? p.getSku() : "",
                    p.getName() != null ? p.getName() : "",
                    p.getDescription() != null ? p.getDescription() : "",
                    p.getAttributes() != null ? p.getAttributes().values().toString() : "")
                    .toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (!haystack.contains(term)) {
                    return false;
                }
            }
            return true;
        }
    }

    private class StoreCartAdapter implements CartAdapter {
        @Override
        public Cart getCart(String cartId) {
            Cart cart = carts.get(cartId);
            if (cart == null) {
                throw new NoSuchElementException("Cart not found: " + cartId);
            }
            return copyCart(cart);
        }

        @Override
        public Cart createCart(String customerId) {
            String id = "cart-" + cartSeq.incrementAndGet();
            Cart cart = Cart.builder()
                    .id(id)
                    .customerId(customerId)
                    .currency(DEFAULT_CURRENCY)
                    .items(new ArrayList<>())
                    .totalAmount(BigDecimal.ZERO.setScale(2))
                    .build();
            carts.put(id, cart);
            return copyCart(cart);
        }

        @Override
        public synchronized Cart addToCart(String cartId, String productId, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than 0");
            }
            Cart cart = carts.get(cartId);
            if (cart == null) {
                throw new NoSuchElementException("Cart not found: " + cartId);
            }
            Product product = catalogAdapter.getProductById(productId)
                    .orElseThrow(() -> new NoSuchElementException("Product not found: " + productId));

            Optional<Cart.CartItem> existing = cart.getItems().stream()
                    .filter(i -> product.getId().equals(i.getProductId()))
                    .findFirst();

            if (existing.isPresent()) {
                Cart.CartItem item = existing.get();
                int newQty = item.getQuantity() + quantity;
                item.setQuantity(newQty);
                item.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(newQty)));
            } else {
                Cart.CartItem item = Cart.CartItem.builder()
                        .id("item-" + itemSeq.incrementAndGet())
                        .productId(product.getId())
                        .name(product.getName())
                        .sku(product.getSku())
                        .quantity(quantity)
                        .unitPrice(product.getPrice())
                        .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                        .imageUrl(product.getImageUrl())
                        .build();
                cart.getItems().add(item);
            }
            cart.setTotalAmount(calculateTotal(cart.getItems()));
            return copyCart(cart);
        }

        @Override
        public synchronized Cart removeFromCart(String cartId, String itemId) {
            Cart cart = carts.get(cartId);
            if (cart == null) {
                throw new NoSuchElementException("Cart not found: " + cartId);
            }
            boolean removed = cart.getItems().removeIf(
                    i -> itemId.equals(i.getId()) || itemId.equals(i.getProductId()));
            if (!removed) {
                throw new NoSuchElementException("Cart item not found: " + itemId);
            }
            cart.setTotalAmount(calculateTotal(cart.getItems()));
            return copyCart(cart);
        }

        @Override
        public synchronized Cart updateItemQuantity(String cartId, String itemId, int quantity) {
            if (quantity <= 0) {
                return removeFromCart(cartId, itemId);
            }
            Cart cart = carts.get(cartId);
            if (cart == null) {
                throw new NoSuchElementException("Cart not found: " + cartId);
            }
            Cart.CartItem item = cart.getItems().stream()
                    .filter(i -> itemId.equals(i.getId()) || itemId.equals(i.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Cart item not found: " + itemId));

            item.setQuantity(quantity);
            item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
            cart.setTotalAmount(calculateTotal(cart.getItems()));
            return copyCart(cart);
        }
    }

    private class StoreCheckoutAdapter implements CheckoutAdapter {
        @Override
        public Checkout createCheckout(Cart cart) {
            if (cart == null) {
                throw new IllegalArgumentException("Cart must not be null");
            }
            Cart resolved = cart.getId() != null && carts.containsKey(cart.getId())
                    ? cartAdapter.getCart(cart.getId())
                    : copyCart(cart);

            String id = "chk-" + checkoutSeq.incrementAndGet();
            Checkout.CheckoutStatus initialStatus = resolved.getItems() == null || resolved.getItems().isEmpty()
                    ? Checkout.CheckoutStatus.INCOMPLETE
                    : Checkout.CheckoutStatus.READY_FOR_COMPLETE;

            Checkout checkout = Checkout.builder()
                    .id(id)
                    .customerId(resolved.getCustomerId())
                    .currency(resolved.getCurrency() != null ? resolved.getCurrency() : DEFAULT_CURRENCY)
                    .items(resolved.getItems())
                    .totalAmount(calculateTotal(resolved.getItems()))
                    .status(initialStatus)
                    .continueUrl("http://localhost:8082/ucp/v1/checkout-sessions/" + id)
                    .expiresAt(Instant.now().plus(Duration.ofHours(6)))
                    .build();
            checkouts.put(id, checkout);
            return checkout;
        }

        @Override
        public Optional<Checkout> getCheckout(String checkoutId) {
            return Optional.ofNullable(checkouts.get(checkoutId));
        }

        @Override
        public synchronized Checkout updateCheckout(String checkoutId, Checkout update) {
            Checkout existing = checkouts.get(checkoutId);
            if (existing == null) {
                throw new NoSuchElementException("Checkout session not found: " + checkoutId);
            }
            if (existing.getStatus() == Checkout.CheckoutStatus.COMPLETED
                    || existing.getStatus() == Checkout.CheckoutStatus.CANCELED) {
                throw new IllegalStateException("Cannot modify checkout in status " + existing.getStatus());
            }
            if (update.getCustomerId() != null) {
                existing.setCustomerId(update.getCustomerId());
            }
            if (update.getCurrency() != null) {
                existing.setCurrency(update.getCurrency());
            }
            if (update.getItems() != null && !update.getItems().isEmpty()) {
                existing.setItems(new ArrayList<>(update.getItems()));
                existing.setTotalAmount(calculateTotal(existing.getItems()));
                existing.setStatus(Checkout.CheckoutStatus.READY_FOR_COMPLETE);
            }
            return existing;
        }

        @Override
        public synchronized Checkout completeCheckout(String checkoutId) {
            Checkout existing = checkouts.get(checkoutId);
            if (existing == null) {
                throw new NoSuchElementException("Checkout session not found: " + checkoutId);
            }
            if (existing.getStatus() == Checkout.CheckoutStatus.COMPLETED) {
                return existing;
            }
            if (existing.getStatus() == Checkout.CheckoutStatus.CANCELED) {
                throw new IllegalStateException("Cannot complete a canceled checkout session");
            }
            if (existing.getItems() == null || existing.getItems().isEmpty()) {
                throw new IllegalStateException("Cannot complete an empty checkout session");
            }

            Order createdOrder = createOrderFromItems(
                    existing.getCustomerId(),
                    existing.getCurrency(),
                    existing.getItems());
            existing.setStatus(Checkout.CheckoutStatus.COMPLETED);
            existing.setOrderId(createdOrder.getId());
            return existing;
        }

        @Override
        public synchronized Checkout cancelCheckout(String checkoutId) {
            Checkout existing = checkouts.get(checkoutId);
            if (existing == null) {
                throw new NoSuchElementException("Checkout session not found: " + checkoutId);
            }
            if (existing.getStatus() == Checkout.CheckoutStatus.COMPLETED) {
                throw new IllegalStateException("Cannot cancel an already completed checkout session");
            }
            existing.setStatus(Checkout.CheckoutStatus.CANCELED);
            return existing;
        }
    }

    private Order createOrderFromItems(String customerId, String currency, List<Cart.CartItem> cartItems) {
        String id = "ord-" + orderSeq.incrementAndGet();
        List<Order.OrderItem> orderItems = cartItems.stream()
                .map(ci -> Order.OrderItem.builder()
                        .id(ci.getId())
                        .productId(ci.getProductId())
                        .name(ci.getName())
                        .sku(ci.getSku())
                        .quantity(ci.getQuantity())
                        .unitPrice(ci.getUnitPrice())
                        .totalPrice(ci.getTotalPrice())
                        .build())
                .toList();

        String email = customerId != null && customers.containsKey(customerId)
                ? customers.get(customerId).getEmail()
                : null;

        Order order = Order.builder()
                .id(id)
                .externalId("ext-" + id)
                .customerId(customerId)
                .customerEmail(email)
                .currency(currency != null ? currency : DEFAULT_CURRENCY)
                .status(Order.OrderStatus.CONFIRMED)
                .createdAt(Instant.now())
                .items(new ArrayList<>(orderItems))
                .totalAmount(calculateTotal(cartItems))
                .build();
        orders.put(id, order);
        return order;
    }

    private class StoreOrderAdapter implements OrderAdapter {
        @Override
        public Order createOrder(String cartId) {
            Cart cart = cartAdapter.getCart(cartId);
            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                throw new IllegalStateException("Cannot create an order from an empty cart: " + cartId);
            }
            return createOrderFromItems(cart.getCustomerId(), cart.getCurrency(), cart.getItems());
        }

        @Override
        public Order getOrder(String orderId) {
            Order order = orders.get(orderId);
            if (order == null) {
                throw new NoSuchElementException("Order not found: " + orderId);
            }
            return order;
        }

        @Override
        public List<Order> getOrdersByCustomer(String customerId) {
            if (customerId == null || customerId.isBlank()) {
                return List.copyOf(orders.values());
            }
            return orders.values().stream()
                    .filter(o -> customerId.equals(o.getCustomerId()))
                    .toList();
        }

        @Override
        public synchronized Order cancelOrder(String orderId) {
            Order order = getOrder(orderId);
            order.setStatus(Order.OrderStatus.CANCELLED);
            return order;
        }
    }

    private class StoreCustomerAdapter implements CustomerAdapter {
        @Override
        public Optional<Customer> getCustomer(String id) {
            if (id == null) {
                return Optional.empty();
            }
            Customer direct = customers.get(id);
            if (direct != null) {
                return Optional.of(direct);
            }
            return customers.values().stream()
                    .filter(c -> id.equalsIgnoreCase(c.getEmail()))
                    .findFirst();
        }

        @Override
        public Customer createCustomer(Customer customer) {
            String id = (customer.getId() != null && !customer.getId().isBlank())
                    ? customer.getId()
                    : "cust-" + customerSeq.incrementAndGet();
            Customer created = Customer.builder()
                    .id(id)
                    .externalId(customer.getExternalId() != null ? customer.getExternalId() : "ext-" + id)
                    .email(customer.getEmail())
                    .firstName(customer.getFirstName())
                    .lastName(customer.getLastName())
                    .phoneNumber(customer.getPhoneNumber())
                    .build();
            customers.put(id, created);
            return created;
        }

        @Override
        public Customer updateCustomer(String id, Customer update) {
            Customer existing = customers.get(id);
            if (existing == null) {
                throw new NoSuchElementException("Customer not found: " + id);
            }
            if (update.getEmail() != null) {
                existing.setEmail(update.getEmail());
            }
            if (update.getFirstName() != null) {
                existing.setFirstName(update.getFirstName());
            }
            if (update.getLastName() != null) {
                existing.setLastName(update.getLastName());
            }
            if (update.getPhoneNumber() != null) {
                existing.setPhoneNumber(update.getPhoneNumber());
            }
            return existing;
        }
    }
}
