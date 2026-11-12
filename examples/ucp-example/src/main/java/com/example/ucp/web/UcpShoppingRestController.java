package com.example.ucp.web;

import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;
import dev.ucomprotocol.model.Customer;
import dev.ucomprotocol.model.Order;
import dev.ucomprotocol.model.Product;
import dev.ucomprotocol.spi.CommerceAdapter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST transport binding ({@code /ucp/v1}) exposing all UCP capabilities
 * backed by the configured {@link CommerceAdapter}.
 */
@RestController
@RequestMapping("/ucp/v1")
public class UcpShoppingRestController {

    private final CommerceAdapter commerceAdapter;

    public UcpShoppingRestController(CommerceAdapter commerceAdapter) {
        this.commerceAdapter = commerceAdapter;
    }

    // --- Catalog (dev.ucp.shopping.catalog.search & lookup) ---

    @GetMapping("/catalog/products")
    public Map<String, Object> listOrSearchProducts(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "category", required = false) String category) {
        List<Product> results = (category != null && !category.isBlank())
                ? commerceAdapter.getCatalogAdapter().getProductsByCategory(category)
                : commerceAdapter.getCatalogAdapter().searchProducts(query);
        return Map.of("items", results, "count", results.size());
    }

    @GetMapping("/catalog/products/{id}")
    public Product getProduct(@PathVariable String id) {
        return commerceAdapter.getCatalogAdapter()
                .getProductById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
    }

    @PostMapping("/catalog/search")
    public Map<String, Object> searchCatalog(@RequestBody(required = false) CatalogSearchRequest request) {
        String query = request != null ? request.query() : null;
        String category = request != null ? request.category() : null;
        List<Product> results = (category != null && !category.isBlank())
                ? commerceAdapter.getCatalogAdapter().getProductsByCategory(category)
                : commerceAdapter.getCatalogAdapter().searchProducts(query);
        return Map.of("items", results, "count", results.size());
    }

    @PostMapping({ "/catalog/lookup", "/catalog/product" })
    public Map<String, Object> lookupCatalog(@RequestBody CatalogLookupRequest request) {
        List<Product> matches = new ArrayList<>();
        if (request.id() != null && !request.id().isBlank()) {
            commerceAdapter.getCatalogAdapter().getProductById(request.id()).ifPresent(matches::add);
        }
        if (request.ids() != null) {
            for (String id : request.ids()) {
                commerceAdapter.getCatalogAdapter().getProductById(id).ifPresent(matches::add);
            }
        }
        return Map.of("items", matches, "count", matches.size());
    }

    // --- Cart (dev.ucp.shopping.cart) ---

    @PostMapping("/carts")
    public ResponseEntity<Cart> createCart(@RequestBody(required = false) CreateCartRequest request) {
        String customerId = request != null ? request.customerId() : null;
        Cart cart = commerceAdapter.getCartAdapter().createCart(customerId);
        if (request != null && request.items() != null) {
            for (LineItemInput item : request.items()) {
                cart = commerceAdapter.getCartAdapter().addToCart(
                        cart.getId(), item.productId(), item.quantity() != null ? item.quantity() : 1);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @GetMapping("/carts/{id}")
    public Cart getCart(@PathVariable String id) {
        return commerceAdapter.getCartAdapter().getCart(id);
    }

    @PutMapping("/carts/{id}")
    public Cart replaceCartItems(@PathVariable String id, @RequestBody CreateCartRequest request) {
        Cart current = commerceAdapter.getCartAdapter().getCart(id);
        for (Cart.CartItem existing : List.copyOf(current.getItems())) {
            current = commerceAdapter.getCartAdapter().removeFromCart(id, existing.getId());
        }
        if (request.items() != null) {
            for (LineItemInput item : request.items()) {
                current = commerceAdapter.getCartAdapter().addToCart(
                        id, item.productId(), item.quantity() != null ? item.quantity() : 1);
            }
        }
        return current;
    }

    @PostMapping("/carts/{id}/items")
    public Cart addCartItem(@PathVariable String id, @RequestBody LineItemInput item) {
        int qty = item.quantity() != null ? item.quantity() : 1;
        return commerceAdapter.getCartAdapter().addToCart(id, item.productId(), qty);
    }

    @PatchMapping("/carts/{id}/items/{itemId}")
    public Cart updateCartItemQuantity(
            @PathVariable String id,
            @PathVariable String itemId,
            @RequestBody QuantityInput input) {
        return commerceAdapter.getCartAdapter().updateItemQuantity(id, itemId, input.quantity());
    }

    @DeleteMapping("/carts/{id}/items/{itemId}")
    public Cart removeCartItem(@PathVariable String id, @PathVariable String itemId) {
        return commerceAdapter.getCartAdapter().removeFromCart(id, itemId);
    }

    @PostMapping("/carts/{id}/cancel")
    public Cart clearCart(@PathVariable String id) {
        Cart current = commerceAdapter.getCartAdapter().getCart(id);
        for (Cart.CartItem existing : List.copyOf(current.getItems())) {
            current = commerceAdapter.getCartAdapter().removeFromCart(id, existing.getId());
        }
        return current;
    }

    // --- Checkout (dev.ucp.shopping.checkout) ---

    @PostMapping("/checkout-sessions")
    public ResponseEntity<Checkout> createCheckout(@RequestBody CreateCheckoutRequest request) {
        Cart cart;
        if (request.cartId() != null && !request.cartId().isBlank()) {
            cart = commerceAdapter.getCartAdapter().getCart(request.cartId());
        } else {
            cart = commerceAdapter.getCartAdapter().createCart(request.customerId());
            if (request.items() != null) {
                for (LineItemInput item : request.items()) {
                    cart = commerceAdapter.getCartAdapter().addToCart(
                            cart.getId(), item.productId(), item.quantity() != null ? item.quantity() : 1);
                }
            }
        }
        Checkout created = commerceAdapter.getCheckoutAdapter().createCheckout(cart);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/checkout-sessions/{id}")
    public Checkout getCheckout(@PathVariable String id) {
        return commerceAdapter.getCheckoutAdapter()
                .getCheckout(id)
                .orElseThrow(() -> new NoSuchElementException("Checkout session not found: " + id));
    }

    @PutMapping("/checkout-sessions/{id}")
    public Checkout updateCheckout(@PathVariable String id, @RequestBody Checkout update) {
        return commerceAdapter.getCheckoutAdapter().updateCheckout(id, update);
    }

    @PostMapping("/checkout-sessions/{id}/complete")
    public Checkout completeCheckout(@PathVariable String id) {
        return commerceAdapter.getCheckoutAdapter().completeCheckout(id);
    }

    @PostMapping("/checkout-sessions/{id}/cancel")
    public Checkout cancelCheckout(@PathVariable String id) {
        return commerceAdapter.getCheckoutAdapter().cancelCheckout(id);
    }

    // --- Orders (dev.ucp.shopping.order) ---

    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = commerceAdapter.getOrderAdapter().createOrder(request.cartId());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable String id) {
        return commerceAdapter.getOrderAdapter().getOrder(id);
    }

    @GetMapping("/orders")
    public Map<String, Object> listOrders(@RequestParam(name = "customerId", required = false) String customerId) {
        List<Order> orders = commerceAdapter.getOrderAdapter().getOrdersByCustomer(customerId);
        return Map.of("items", orders, "count", orders.size());
    }

    @PostMapping("/orders/{id}/cancel")
    public Order cancelOrder(@PathVariable String id) {
        return commerceAdapter.getOrderAdapter().cancelOrder(id);
    }

    // --- Customers / Identity Linking (dev.ucp.common.identity_linking) ---

    @PostMapping("/customers")
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commerceAdapter.getCustomerAdapter().createCustomer(customer));
    }

    @GetMapping("/customers/{id}")
    public Customer getCustomer(@PathVariable String id) {
        return commerceAdapter.getCustomerAdapter()
                .getCustomer(id)
                .orElseThrow(() -> new NoSuchElementException("Customer not found: " + id));
    }

    @PutMapping("/customers/{id}")
    public Customer updateCustomer(@PathVariable String id, @RequestBody Customer customer) {
        return commerceAdapter.getCustomerAdapter().updateCustomer(id, customer);
    }

    // --- Error Handling ---

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "bad_request", "message", ex.getMessage()));
    }

    public record CatalogSearchRequest(String query, String category) {
    }

    public record CatalogLookupRequest(String id, List<String> ids) {
    }

    public record LineItemInput(String productId, Integer quantity) {
    }

    public record QuantityInput(int quantity) {
    }

    public record CreateCartRequest(String customerId, List<LineItemInput> items) {
    }

    public record CreateCheckoutRequest(String cartId, String customerId, List<LineItemInput> items) {
    }

    public record CreateOrderRequest(String cartId) {
    }
}
