package dev.ucomprotocol.discovery;

import dev.ucomprotocol.autoconfigure.UcpProperties;
import dev.ucomprotocol.spi.CatalogAdapter;
import dev.ucomprotocol.spi.CheckoutAdapter;
import dev.ucomprotocol.spi.CommerceAdapter;
import dev.ucomprotocol.spi.CustomerAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class UcpDiscoveryControllerSafeTest {

    @Test
    public void testCapabilitiesResolution() {
        UcpProperties properties = new UcpProperties();
        properties.setProvider("shopify");

        CommerceAdapter mockAdapter = Mockito.mock(CommerceAdapter.class);

        when(mockAdapter.getCatalogAdapter()).thenReturn(Mockito.mock(CatalogAdapter.class));
        when(mockAdapter.getCheckoutAdapter()).thenReturn(Mockito.mock(CheckoutAdapter.class));
        when(mockAdapter.getCustomerAdapter()).thenReturn(Mockito.mock(CustomerAdapter.class));
        when(mockAdapter.getCartAdapter()).thenReturn(null);
        when(mockAdapter.getOrderAdapter()).thenReturn(null);

        UcpDiscoveryController controller = new UcpDiscoveryController(properties, mockAdapter);
        UcpDiscoveryController.DiscoveryResponse response = controller.getDiscoveryInfo();

        assertEquals("2026-08-25", response.ucp().version());
        assertEquals(4, response.ucp().capabilities().size());
        assertTrue(response.ucp().capabilities().containsKey("dev.ucp.shopping.catalog.search"));
        assertTrue(response.ucp().capabilities().containsKey("dev.ucp.shopping.catalog.lookup"));
        assertTrue(response.ucp().capabilities().containsKey("dev.ucp.shopping.checkout"));
        assertTrue(response.ucp().capabilities().containsKey("dev.ucp.common.identity_linking"));
        assertTrue(response.ucp().services().isEmpty());
    }

    @Test
    public void testServicesResolution() {
        UcpProperties properties = new UcpProperties();
        properties.getServices().setRestEndpoint("https://shop.example.com/ucp/v1");
        properties.getServices().setA2aEndpoint("https://shop.example.com/.well-known/agent-card.json");
        properties.getServices().setEmbeddedEnabled(true);

        CommerceAdapter mockAdapter = Mockito.mock(CommerceAdapter.class);
        UcpDiscoveryController controller = new UcpDiscoveryController(properties, mockAdapter);

        var services = controller.getDiscoveryInfo().ucp().services();

        assertEquals(1, services.size());
        var bindings = services.get("dev.ucp.shopping");
        assertEquals(3, bindings.size());
        assertEquals("rest", bindings.get(0).transport());
        assertEquals("https://shop.example.com/ucp/v1", bindings.get(0).endpoint());
        assertEquals("https://ucp.dev/2026-08-25/services/shopping/rest.openapi.json", bindings.get(0).schema());
        assertEquals("a2a", bindings.get(1).transport());
        assertNull(bindings.get(1).schema());
        assertEquals("embedded", bindings.get(2).transport());
        assertNull(bindings.get(2).endpoint());
        assertEquals("https://ucp.dev/2026-08-25/services/shopping/embedded.openrpc.json", bindings.get(2).schema());
    }
}
