package com.example.ucp.mcp;

import com.example.ucp.ap2.DemoAp2WalletService;
import dev.ucomprotocol.ap2.Ap2MandateException;
import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;
import dev.ucomprotocol.model.Customer;
import dev.ucomprotocol.model.Order;
import dev.ucomprotocol.model.Product;
import dev.ucomprotocol.spi.CommerceAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Model Context Protocol (MCP) Streamable HTTP JSON-RPC 2.0 server endpoint
 * ({@code /ucp/mcp}) allowing any MCP-capable AI agent (Claude Code, Claude
 * Desktop, Cursor, Gemini CLI, Antigravity, etc.) to discover and invoke UCP
 * shopping, AP2 cryptographic checkout mandates, and identity capabilities.
 */
@RestController
@RequestMapping("/ucp/mcp")
public class UcpMcpController {

    private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";

    private final CommerceAdapter commerceAdapter;
    private final ObjectMapper objectMapper;
    private final DemoAp2WalletService demoWalletService;

    public UcpMcpController(
            CommerceAdapter commerceAdapter,
            ObjectMapper objectMapper,
            ObjectProvider<DemoAp2WalletService> demoWalletProvider) {
        this.commerceAdapter = commerceAdapter;
        this.objectMapper = objectMapper;
        this.demoWalletService = demoWalletProvider.getIfAvailable();
    }

    /**
     * Per MCP Streamable HTTP specification, returning 405 on GET indicates
     * synchronous JSON responses are returned directly on POST without requiring
     * a standalone server-push SSE stream.
     */
    @GetMapping
    public ResponseEntity<Void> handleSseProbe() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleJsonRpc(@RequestBody Map<String, Object> request) {
        String method = stringArg(request, "method");
        Object id = request.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = request.get("params") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        // JSON-RPC notifications (no id, e.g., notifications/initialized) return 202 Accepted
        if (!request.containsKey("id")) {
            return ResponseEntity.accepted().build();
        }

        if (method == null || method.isBlank()) {
            return ResponseEntity.ok(rpcError(id, -32600, "Invalid Request: missing method"));
        }

        try {
            return switch (method) {
                case "initialize" -> ResponseEntity.ok(rpcResult(id, handleInitialize(params)));
                case "ping" -> ResponseEntity.ok(rpcResult(id, Map.of()));
                case "tools/list" -> ResponseEntity.ok(rpcResult(id, Map.of("tools", buildToolDefinitions())));
                case "tools/call" -> ResponseEntity.ok(rpcResult(id, handleToolCall(params)));
                default -> ResponseEntity.ok(rpcError(id, -32601, "Method not found: " + method));
            };
        } catch (Exception ex) {
            return ResponseEntity.ok(rpcError(id, -32603, ex.getMessage()));
        }
    }

    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        String requestedVersion = stringArg(params, "protocolVersion");
        String negotiatedVersion = (requestedVersion != null && !requestedVersion.isBlank())
                ? requestedVersion
                : DEFAULT_PROTOCOL_VERSION;

        String ap2Note = demoWalletService != null
                ? " AP2 Mandates are active: sign a checkout mandate via sign_ap2_checkout_mandate before calling complete_checkout."
                : "";

        return Map.of(
                "protocolVersion", negotiatedVersion,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of(
                        "name", "ucp-java-sample-store",
                        "version", "0.3.0"),
                "instructions",
                "Universal Commerce Protocol (UCP 2026-08-25) sample store. Search the catalog, manage carts, run checkout sessions, inspect orders, and manage customer identities."
                        + ap2Note);
    }

    private Map<String, Object> handleToolCall(Map<String, Object> params) {
        String name = stringArg(params, "name");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = params.get("arguments") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing tool name");
        }

        try {
            Object payload = executeTool(name, args);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", json)),
                    "isError", false);
        } catch (Ap2MandateException ex) {
            return Map.of(
                    "content", List.of(Map.of("type", "text",
                            "text", "AP2 Error [" + ex.getErrorCode().getCode() + "]: " + ex.getMessage())),
                    "isError", true);
        } catch (NoSuchElementException | IllegalArgumentException | IllegalStateException ex) {
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Error: " + ex.getMessage())),
                    "isError", true);
        }
    }

    private Object executeTool(String name, Map<String, Object> args) {
        return switch (name) {
            case "search_catalog" -> {
                String category = stringArg(args, "category");
                String query = stringArg(args, "query");
                List<Product> items = (category != null && !category.isBlank())
                        ? commerceAdapter.getCatalogAdapter().getProductsByCategory(category)
                        : commerceAdapter.getCatalogAdapter().searchProducts(query);
                yield Map.of("items", items, "count", items.size());
            }
            case "lookup_catalog", "get_product" -> {
                String id = requireString(args, "id");
                yield commerceAdapter.getCatalogAdapter()
                        .getProductById(id)
                        .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));
            }
            case "create_cart" -> commerceAdapter.getCartAdapter().createCart(stringArg(args, "customerId"));
            case "get_cart" -> commerceAdapter.getCartAdapter().getCart(requireString(args, "cartId"));
            case "update_cart" -> {
                String cartId = requireString(args, "cartId");
                String action = stringArg(args, "action");
                if (action == null || "add".equalsIgnoreCase(action)) {
                    String productId = requireString(args, "productId");
                    int qty = intArg(args, "quantity", 1);
                    yield commerceAdapter.getCartAdapter().addToCart(cartId, productId, qty);
                } else if ("update".equalsIgnoreCase(action)) {
                    String itemId = stringArg(args, "itemId");
                    if (itemId == null) {
                        itemId = requireString(args, "productId");
                    }
                    int qty = intArg(args, "quantity", 1);
                    yield commerceAdapter.getCartAdapter().updateItemQuantity(cartId, itemId, qty);
                } else if ("remove".equalsIgnoreCase(action)) {
                    String itemId = stringArg(args, "itemId");
                    if (itemId == null) {
                        itemId = requireString(args, "productId");
                    }
                    yield commerceAdapter.getCartAdapter().removeFromCart(cartId, itemId);
                } else {
                    throw new IllegalArgumentException("Unsupported action: " + action);
                }
            }
            case "cancel_cart" -> {
                String cartId = requireString(args, "cartId");
                Cart current = commerceAdapter.getCartAdapter().getCart(cartId);
                for (Cart.CartItem item : List.copyOf(current.getItems())) {
                    current = commerceAdapter.getCartAdapter().removeFromCart(cartId, item.getId());
                }
                yield current;
            }
            case "create_checkout" -> {
                String cartId = requireString(args, "cartId");
                Cart cart = commerceAdapter.getCartAdapter().getCart(cartId);
                yield commerceAdapter.getCheckoutAdapter().createCheckout(cart);
            }
            case "get_checkout" -> {
                String checkoutId = requireString(args, "checkoutId");
                yield commerceAdapter.getCheckoutAdapter()
                        .getCheckout(checkoutId)
                        .orElseThrow(() -> new NoSuchElementException("Checkout session not found: " + checkoutId));
            }
            case "update_checkout" -> {
                String checkoutId = requireString(args, "checkoutId");
                Checkout patch = Checkout.builder()
                        .customerId(stringArg(args, "customerId"))
                        .currency(stringArg(args, "currency"))
                        .build();
                yield commerceAdapter.getCheckoutAdapter().updateCheckout(checkoutId, patch);
            }
            case "sign_ap2_checkout_mandate" -> {
                if (demoWalletService == null) {
                    throw new IllegalStateException("AP2 is disabled (ucp.ap2.enabled=false)");
                }
                yield demoWalletService.signCheckoutMandate(requireString(args, "checkoutId"));
            }
            case "complete_checkout" -> {
                String checkoutId = requireString(args, "checkoutId");
                String mandate = stringArg(args, "checkoutMandate");
                Checkout.Ap2Data ap2 = (mandate != null && !mandate.isBlank())
                        ? Checkout.Ap2Data.builder().checkoutMandate(mandate).build()
                        : null;
                yield commerceAdapter.getCheckoutAdapter().completeCheckout(checkoutId, ap2);
            }
            case "cancel_checkout" -> commerceAdapter.getCheckoutAdapter()
                    .cancelCheckout(requireString(args, "checkoutId"));
            case "get_order" -> commerceAdapter.getOrderAdapter()
                    .getOrder(requireString(args, "orderId"));
            case "list_customer_orders" -> {
                List<Order> orders = commerceAdapter.getOrderAdapter()
                        .getOrdersByCustomer(stringArg(args, "customerId"));
                yield Map.of("items", orders, "count", orders.size());
            }
            case "cancel_order" -> commerceAdapter.getOrderAdapter()
                    .cancelOrder(requireString(args, "orderId"));
            case "get_customer" -> {
                String id = requireString(args, "customerId");
                yield commerceAdapter.getCustomerAdapter()
                        .getCustomer(id)
                        .orElseThrow(() -> new NoSuchElementException("Customer not found: " + id));
            }
            case "create_customer" -> commerceAdapter.getCustomerAdapter()
                    .createCustomer(Customer.builder()
                            .email(requireString(args, "email"))
                            .firstName(stringArg(args, "firstName"))
                            .lastName(stringArg(args, "lastName"))
                            .phoneNumber(stringArg(args, "phoneNumber"))
                            .build());
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("search_catalog",
                "Search the product catalog by keyword query or filter by category (coffee, electronics, accessories). Omit query to list all products.",
                Map.of(
                        "query", prop("string", "Search keywords (e.g. 'espresso', 'grinder', 'keyboard')"),
                        "category", prop("string", "Optional category filter")),
                List.of()));
        tools.add(tool("lookup_catalog",
                "Look up a single product by product ID or SKU.",
                Map.of("id", prop("string", "Product ID (e.g. 'prod-espresso-01') or SKU")),
                List.of("id")));
        tools.add(tool("create_cart",
                "Create a new shopping cart, optionally linked to a customerId.",
                Map.of("customerId", prop("string", "Optional customer ID (e.g. 'cust-demo-01')")),
                List.of()));
        tools.add(tool("get_cart",
                "Retrieve the current contents and total amount of a cart.",
                Map.of("cartId", prop("string", "Cart ID")),
                List.of("cartId")));
        tools.add(tool("update_cart",
                "Add, update quantity, or remove an item in a shopping cart.",
                Map.of(
                        "cartId", prop("string", "Cart ID"),
                        "action", prop("string", "One of: 'add', 'update', 'remove' (defaults to 'add')"),
                        "productId", prop("string", "Product ID to add/update/remove"),
                        "itemId", prop("string", "Cart line item ID (optional if productId is supplied)"),
                        "quantity", prop("integer", "Quantity (default 1)")),
                List.of("cartId")));
        tools.add(tool("cancel_cart",
                "Clear all items from a shopping cart.",
                Map.of("cartId", prop("string", "Cart ID")),
                List.of("cartId")));
        tools.add(tool("create_checkout",
                "Create a UCP checkout session from an existing non-empty cart. When AP2 is enabled, returns ap2.merchant_authorization.",
                Map.of("cartId", prop("string", "Cart ID to convert into a checkout session")),
                List.of("cartId")));
        tools.add(tool("get_checkout",
                "Get the status, line items, totals, and AP2 merchant signature of a checkout session.",
                Map.of("checkoutId", prop("string", "Checkout session ID")),
                List.of("checkoutId")));
        tools.add(tool("update_checkout",
                "Update customer or currency metadata on an active checkout session.",
                Map.of(
                        "checkoutId", prop("string", "Checkout session ID"),
                        "customerId", prop("string", "Customer ID"),
                        "currency", prop("string", "ISO 4217 currency code")),
                List.of("checkoutId")));
        if (demoWalletService != null) {
            tools.add(tool("sign_ap2_checkout_mandate",
                    "Sign an SD-JWT+kb AP2 checkout mandate (ap2.checkout_mandate) using the demo agent wallet over a merchant-signed checkout session.",
                    Map.of("checkoutId", prop("string", "Checkout session ID to sign an AP2 mandate for")),
                    List.of("checkoutId")));
        }
        tools.add(tool("complete_checkout",
                "Complete a checkout session and place the final order. Requires checkoutMandate when AP2 is enabled.",
                Map.of(
                        "checkoutId", prop("string", "Checkout session ID"),
                        "checkoutMandate", prop("string",
                                "SD-JWT+kb AP2 checkout mandate (required when dev.ucp.common.payment.ap2_mandate is enabled)")),
                List.of("checkoutId")));
        tools.add(tool("cancel_checkout",
                "Cancel an active checkout session.",
                Map.of("checkoutId", prop("string", "Checkout session ID")),
                List.of("checkoutId")));
        tools.add(tool("get_order",
                "Fetch placed order details and status by order ID.",
                Map.of("orderId", prop("string", "Order ID")),
                List.of("orderId")));
        tools.add(tool("list_customer_orders",
                "List orders placed by a customer (or all orders if customerId is omitted).",
                Map.of("customerId", prop("string", "Optional customer ID")),
                List.of()));
        tools.add(tool("cancel_order",
                "Cancel an existing order by order ID.",
                Map.of("orderId", prop("string", "Order ID")),
                List.of("orderId")));
        tools.add(tool("get_customer",
                "Look up a customer profile by customer ID or email address.",
                Map.of("customerId", prop("string", "Customer ID (e.g. 'cust-demo-01') or email")),
                List.of("customerId")));
        tools.add(tool("create_customer",
                "Create/link a new customer identity record.",
                Map.of(
                        "email", prop("string", "Customer email address"),
                        "firstName", prop("string", "First name"),
                        "lastName", prop("string", "Last name"),
                        "phoneNumber", prop("string", "Phone number")),
                List.of("email")));
        return List.copyOf(tools);
    }

    private static Map<String, Object> tool(
            String name,
            String description,
            Map<String, Object> properties,
            List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", schema);
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> rpcResult(Object id, Object result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jsonrpc", "2.0");
        map.put("id", id);
        map.put("result", result);
        return map;
    }

    private static Map<String, Object> rpcError(Object id, int code, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jsonrpc", "2.0");
        map.put("id", id);
        map.put("error", Map.of("code", code, "message", message != null ? message : "Internal error"));
        return map;
    }

    private static String stringArg(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private static String requireString(Map<String, Object> map, String key) {
        String val = stringArg(map, key);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return val;
    }

    private static int intArg(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            return Integer.parseInt(s);
        }
        return defaultValue;
    }
}
