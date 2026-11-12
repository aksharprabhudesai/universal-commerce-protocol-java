package dev.ucomprotocol.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ucomprotocol.autoconfigure.UcpProperties;
import dev.ucomprotocol.spi.CommerceAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller that exposes the Universal Commerce Protocol (UCP) discovery
 * endpoint.
 * <p>
 * This endpoint is available at {@code /.well-known/ucp} and returns the
 * business profile of this commerce service. It adheres to the UCP
 * specification version {@value #UCP_VERSION}, using the canonical
 * namespace {@code dev.ucp} for all capabilities and services.
 * <p>
 * Capabilities are dynamically discovered based on the registered
 * {@link CommerceAdapter}:
 * <ul>
 * <li>{@code dev.ucp.shopping.catalog.search} and
 * {@code dev.ucp.shopping.catalog.lookup}: Present if
 * {@link CommerceAdapter#getCatalogAdapter()} is not null.</li>
 * <li>{@code dev.ucp.shopping.cart}: Present if
 * {@link CommerceAdapter#getCartAdapter()} is not null.</li>
 * <li>{@code dev.ucp.shopping.checkout}: Present if
 * {@link CommerceAdapter#getCheckoutAdapter()} is not null.</li>
 * <li>{@code dev.ucp.shopping.order}: Present if
 * {@link CommerceAdapter#getOrderAdapter()} is not null.</li>
 * <li>{@code dev.ucp.common.identity_linking}: Present if
 * {@link CommerceAdapter#getCustomerAdapter()} is not null.</li>
 * </ul>
 * <p>
 * Services ({@code dev.ucp.shopping}) are advertised for each transport
 * endpoint configured under {@code ucp.services.*} in
 * {@link UcpProperties}.
 */
@RestController
@RequestMapping("/.well-known/ucp")
public class UcpDiscoveryController {

    /**
     * The UCP specification version implemented by this library.
     */
    public static final String UCP_VERSION = "2026-08-25";

    /**
     * Name of the UCP shopping service.
     */
    public static final String SHOPPING_SERVICE = "dev.ucp.shopping";

    private static final String BASE_URL = "https://ucp.dev/" + UCP_VERSION;
    private static final String OVERVIEW_SPEC = BASE_URL + "/specification/overview/";

    private final UcpProperties ucpProperties;
    private final CommerceAdapter commerceAdapter;

    /**
     * Creates a new {@link UcpDiscoveryController}.
     *
     * @param ucpProperties   the UCP configuration properties
     * @param commerceAdapter the active commerce adapter
     */
    public UcpDiscoveryController(UcpProperties ucpProperties, CommerceAdapter commerceAdapter) {
        this.ucpProperties = ucpProperties;
        this.commerceAdapter = commerceAdapter;
    }

    /**
     * Returns the UCP discovery profile for this service.
     *
     * @return the discovery profile response
     */
    @GetMapping
    public DiscoveryResponse getDiscoveryInfo() {
        Map<String, List<Capability>> capabilities = new LinkedHashMap<>();

        if (commerceAdapter.getCatalogAdapter() != null) {
            capabilities.put("dev.ucp.shopping.catalog.search", List.of(capability(
                    "/specification/shopping/catalog/search/", "/schemas/shopping/catalog_search.json")));
            capabilities.put("dev.ucp.shopping.catalog.lookup", List.of(capability(
                    "/specification/shopping/catalog/lookup/", "/schemas/shopping/catalog_lookup.json")));
        }
        if (commerceAdapter.getCartAdapter() != null) {
            capabilities.put("dev.ucp.shopping.cart", List.of(capability(
                    "/specification/shopping/cart/", "/schemas/shopping/cart.json")));
        }
        if (commerceAdapter.getCheckoutAdapter() != null) {
            capabilities.put("dev.ucp.shopping.checkout", List.of(capability(
                    "/specification/shopping/checkout/", "/schemas/shopping/checkout.json")));
        }
        if (commerceAdapter.getOrderAdapter() != null) {
            capabilities.put("dev.ucp.shopping.order", List.of(capability(
                    "/specification/shopping/order/", "/schemas/shopping/order.json")));
        }
        if (commerceAdapter.getCustomerAdapter() != null) {
            capabilities.put("dev.ucp.common.identity_linking", List.of(capability(
                    "/specification/common/identity-linking/", "/schemas/common/identity_linking.json")));
        }

        return new DiscoveryResponse(new UcpInfo(UCP_VERSION, resolveServices(), capabilities));
    }

    private Map<String, List<Service>> resolveServices() {
        UcpProperties.Services config = ucpProperties.getServices();
        List<Service> bindings = new ArrayList<>();
        if (hasText(config.getRestEndpoint())) {
            bindings.add(new Service(UCP_VERSION, OVERVIEW_SPEC, "rest", config.getRestEndpoint(),
                    BASE_URL + "/services/shopping/rest.openapi.json"));
        }
        if (hasText(config.getMcpEndpoint())) {
            bindings.add(new Service(UCP_VERSION, OVERVIEW_SPEC, "mcp", config.getMcpEndpoint(),
                    BASE_URL + "/services/shopping/mcp.openrpc.json"));
        }
        if (hasText(config.getA2aEndpoint())) {
            bindings.add(new Service(UCP_VERSION, OVERVIEW_SPEC, "a2a", config.getA2aEndpoint(), null));
        }
        if (config.isEmbeddedEnabled()) {
            bindings.add(new Service(UCP_VERSION, OVERVIEW_SPEC, "embedded", null,
                    BASE_URL + "/services/shopping/embedded.openrpc.json"));
        }
        if (bindings.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<Service>> services = new LinkedHashMap<>();
        services.put(SHOPPING_SERVICE, List.copyOf(bindings));
        return services;
    }

    private static Capability capability(String specPath, String schemaPath) {
        return new Capability(UCP_VERSION, BASE_URL + specPath, BASE_URL + schemaPath);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Top-level response wrapper for UCP discovery.
     *
     * @param ucp the protocol information object
     */
    public record DiscoveryResponse(UcpInfo ucp) {
    }

    /**
     * Detailed UCP protocol information.
     *
     * @param version      The protocol version (e.g., 2026-08-25).
     * @param services     Service registry keyed by reverse-domain name
     *                     (e.g., {@code dev.ucp.shopping}).
     * @param capabilities Capability registry keyed by reverse-domain name
     *                     (e.g., {@code dev.ucp.shopping.checkout}).
     */
    public record UcpInfo(String version, Map<String, List<Service>> services,
            Map<String, List<Capability>> capabilities) {
    }

    /**
     * Details about a specific capability version.
     *
     * @param version          The version of the capability spec supported.
     * @param spec             The URL to the official UCP specification for this
     *                         capability.
     * @param schema           The URL to the JSON Schema for this capability.
     * @param parentCapability Optional parent capability name for extension
     *                         capabilities (serialized as {@code extends}).
     * @param config           Optional capability-specific configuration map.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Capability(
            String version,
            String spec,
            String schema,
            @JsonProperty("extends") String parentCapability,
            Map<String, Object> config) {

        /**
         * Creates a root capability entry without {@code extends} or {@code config}.
         *
         * @param version The version of the capability spec supported.
         * @param spec    The URL to the official UCP specification for this capability.
         * @param schema  The URL to the JSON Schema for this capability.
         */
        public Capability(String version, String spec, String schema) {
            this(version, spec, schema, null, null);
        }
    }

    /**
     * Details about a service transport binding.
     *
     * @param version   The version of the service spec supported.
     * @param spec      The URL to the official UCP specification for this service.
     * @param transport The transport protocol ({@code rest}, {@code mcp},
     *                  {@code a2a} or {@code embedded}).
     * @param endpoint  The endpoint URL for this transport binding.
     * @param schema    The URL to the schema describing this transport binding.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Service(String version, String spec, String transport, String endpoint, String schema) {
    }
}
