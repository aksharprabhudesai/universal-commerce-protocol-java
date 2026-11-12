package dev.ucomprotocol.discovery;

import dev.ucomprotocol.autoconfigure.UcpAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ucp.services.rest-endpoint=https://shop.example.com/ucp/v1")
@AutoConfigureMockMvc
public class UcpDiscoveryControllerTest {

    @SpringBootApplication
    @Import(UcpAutoConfiguration.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnDiscoveryInfo() throws Exception {
        mockMvc.perform(get("/.well-known/ucp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ucp.version").value("2026-08-25"))
                .andExpect(jsonPath("$.ucp.capabilities").isMap())
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.catalog.search'][0].version")
                        .value("2026-08-25"))
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.cart'][0].schema")
                        .value("https://ucp.dev/2026-08-25/schemas/shopping/cart.json"))
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.checkout'][0].schema")
                        .value("https://ucp.dev/2026-08-25/schemas/shopping/checkout.json"))
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.shopping.order']").isArray())
                .andExpect(jsonPath("$.ucp.capabilities['dev.ucp.common.identity_linking']").isArray())
                .andExpect(jsonPath("$.ucp.services['dev.ucp.shopping'][0].transport").value("rest"))
                .andExpect(jsonPath("$.ucp.services['dev.ucp.shopping'][0].endpoint")
                        .value("https://shop.example.com/ucp/v1"));
    }
}
