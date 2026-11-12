package com.example.ucp.a2a;

import dev.ucomprotocol.autoconfigure.UcpProperties;
import dev.ucomprotocol.discovery.UcpDiscoveryController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes an Agent-to-Agent (A2A) Agent Card at
 * {@code /.well-known/agent-card.json} advertising the sample store's UCP
 * shopping capabilities and transport endpoints.
 */
@RestController
public class A2aAgentCardController {

    private final UcpProperties properties;

    public A2aAgentCardController(UcpProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/.well-known/agent-card.json")
    public Map<String, Object> getAgentCard() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", "UCP Java Sample Storefront Agent");
        card.put("description",
                "Reference Universal Commerce Protocol (UCP " + UcpDiscoveryController.UCP_VERSION
                        + ") merchant service supporting catalog search, cart management, checkout sessions, order tracking, and customer identity linking.");
        card.put("url", properties.getServices().getRestEndpoint());
        card.put("version", "0.2.0");
        card.put("protocolVersion", UcpDiscoveryController.UCP_VERSION);
        card.put("discoveryUrl", "http://localhost:8082/.well-known/ucp");
        card.put("mcpEndpoint", properties.getServices().getMcpEndpoint());
        card.put("defaultInputModes", List.of("application/json", "text/plain"));
        card.put("defaultOutputModes", List.of("application/json"));
        card.put("capabilities", Map.of("streaming", false, "pushNotifications", false));
        card.put("skills", List.of(
                Map.of(
                        "id", "dev.ucp.shopping.catalog",
                        "name", "Catalog Search & Lookup",
                        "description", "Search products by keyword/category or look up product details by ID or SKU.",
                        "examples", List.of(
                                "Search for espresso machines and grinders",
                                "Look up product prod-espresso-01")),
                Map.of(
                        "id", "dev.ucp.shopping.cart",
                        "name", "Shopping Cart Management",
                        "description", "Create carts and add, update, or remove line items.",
                        "examples", List.of("Create a cart for cust-demo-01 and add 2 ceramic espresso cups")),
                Map.of(
                        "id", "dev.ucp.shopping.checkout",
                        "name", "Checkout Lifecycle",
                        "description", "Initiate, update, complete, or cancel UCP checkout sessions.",
                        "examples", List.of("Create a checkout session for my cart and complete the purchase")),
                Map.of(
                        "id", "dev.ucp.shopping.order",
                        "name", "Order Lookup & Cancellation",
                        "description", "Inspect confirmed orders by ID/customer or cancel an order.",
                        "examples", List.of("Check the status of my placed order")),
                Map.of(
                        "id", "dev.ucp.common.identity_linking",
                        "name", "Customer Identity Linking",
                        "description", "Retrieve or register customer profiles for personalized orders.",
                        "examples", List.of("Look up customer cust-demo-01"))));
        return card;
    }
}
