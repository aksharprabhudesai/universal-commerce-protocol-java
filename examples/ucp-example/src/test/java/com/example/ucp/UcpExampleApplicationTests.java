package com.example.ucp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UcpExampleApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void discoveryAndAgentCardAdvertiseAllCapabilitiesAndTransports() throws Exception {
        mockMvc.perform(get("/.well-known/ucp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ucp.version").value("2026-08-25"))
                .andExpect(jsonPath("$.ucp.services['dev.ucp.shopping'].length()").value(3))
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.catalog.search']").isArray())
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.catalog.lookup']").isArray())
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.cart']").isArray())
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.checkout']").isArray())
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.order']").isArray())
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.common.identity_linking']").isArray());

        mockMvc.perform(get("/.well-known/agent-card.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolVersion").value("2026-08-25"))
                .andExpect(jsonPath("$.skills.length()").value(5));
    }

    @Test
    void fullRestShoppingJourneyCompletesOrder() throws Exception {
        // 1. Search catalog
        mockMvc.perform(post("/ucp/v1/catalog/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"espresso\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        // 2. Create cart with initial items for demo customer
        MvcResult cartRes = mockMvc.perform(post("/ucp/v1/carts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "cust-demo-01",
                                  "items": [
                                    {"productId": "prod-grinder-02", "quantity": 1},
                                    {"productId": "prod-mugs-03", "quantity": 2}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(217.00))
                .andReturn();

        String cartId = objectMapper.readTree(cartRes.getResponse().getContentAsString()).get("id").asString();

        // 3. Create checkout session from cart
        MvcResult chkRes = mockMvc.perform(post("/ucp/v1/checkout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY_FOR_COMPLETE"))
                .andExpect(jsonPath("$.totalAmount").value(217.00))
                .andReturn();

        String checkoutId = objectMapper.readTree(chkRes.getResponse().getContentAsString()).get("id").asString();

        // 4. Complete checkout -> creates confirmed order
        MvcResult completeRes = mockMvc.perform(post("/ucp/v1/checkout-sessions/" + checkoutId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andReturn();

        String orderId = objectMapper.readTree(completeRes.getResponse().getContentAsString()).get("orderId").asString();

        // 5. Inspect order & customer email populated via identity linking
        mockMvc.perform(get("/ucp/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerEmail").value("alex@example.com"))
                .andExpect(jsonPath("$.totalAmount").value(217.00));

        // 6. Cancel order
        mockMvc.perform(post("/ucp/v1/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void fullMcpAgentJourneyOverJsonRpc() throws Exception {
        // 1. MCP initialize
        mockMvc.perform(post("/ucp/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-03-26"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("ucp-java-sample-store"));

        // 2. MCP notifications/initialized -> 202 Accepted
        mockMvc.perform(post("/ucp/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","method":"notifications/initialized"}
                                """))
                .andExpect(status().isAccepted());

        // 3. MCP tools/list
        mockMvc.perform(post("/ucp/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools.length()").value(16));

        // 4. Create cart via MCP tool call
        JsonNode cartPayload = callMcpTool(3, "create_cart", "{\"customerId\":\"cust-demo-01\"}");
        String cartId = cartPayload.get("id").asString();
        assertThat(cartId).startsWith("cart-");

        // 5. Add espresso machine via update_cart
        JsonNode updatedCart = callMcpTool(4, "update_cart",
                "{\"cartId\":\"" + cartId + "\",\"action\":\"add\",\"productId\":\"prod-espresso-01\",\"quantity\":1}");
        assertThat(updatedCart.get("totalAmount").decimalValue()).isEqualByComparingTo("499.00");

        // 6. Create & complete checkout via MCP tools
        JsonNode checkout = callMcpTool(5, "create_checkout", "{\"cartId\":\"" + cartId + "\"}");
        String checkoutId = checkout.get("id").asString();

        JsonNode completed = callMcpTool(6, "complete_checkout", "{\"checkoutId\":\"" + checkoutId + "\"}");
        assertThat(completed.get("status").asString()).isEqualTo("COMPLETED");
        String orderId = completed.get("orderId").asString();

        // 7. Verify order via get_order tool
        JsonNode order = callMcpTool(7, "get_order", "{\"orderId\":\"" + orderId + "\"}");
        assertThat(order.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("499.00");
    }

    private JsonNode callMcpTool(int id, String toolName, String argsJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/ucp/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":" + id
                                + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + toolName
                                + "\",\"arguments\":" + argsJson + "}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String text = root.at("/result/content/0/text").asString();
        return objectMapper.readTree(text);
    }
}
