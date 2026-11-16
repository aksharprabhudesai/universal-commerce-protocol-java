package dev.ucomprotocol.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ucomprotocol.ap2.Ap2MandateService;
import dev.ucomprotocol.ap2.JwkKey;
import dev.ucomprotocol.autoconfigure.UcpProperties;
import dev.ucomprotocol.spi.CommerceAdapter;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller that exposes the Universal Commerce Protocol (UCP) discovery
 * endpoint.
 * <p>
 * This endpoint is available at {@code /.well-known/ucp} and returns the
 * business profile of this commerce service conforming to
 * {@code ucp.json#/$defs/business_schema} in UCP specification version
 * {@value #UCP_VERSION}, including required {@code ucp.services} and
 * {@code ucp.payment_handlers} registries, optional
 * {@code ucp.supported_versions}, dynamic {@code ucp.capabilities}, and
 * top-level {@code keys[]} (RFC 7517 / RFC 8037 JWK Set).
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

    /**
     * Canonical capability identifier for the AP2 Mandates payment extension.
     */
    public static final String AP2_MANDATE_CAPABILITY = "dev.ucp.common.payment.ap2_mandate";

    private static final String BASE_URL = "https://ucp.dev/" + UCP_VERSION;
    private static final String OVERVIEW_SPEC = BASE_URL + "/specification/overview/";

    private final UcpProperties ucpProperties;
    private final CommerceAdapter commerceAdapter;
    private final Ap2MandateService ap2MandateService;

    /**
     * Creates a new {@link UcpDiscoveryController} without AP2 extension keys.
     *
     * @param ucpProperties   the UCP configuration properties
     * @param commerceAdapter the active commerce adapter
     */
    public UcpDiscoveryController(UcpProperties ucpProperties, CommerceAdapter commerceAdapter) {
        this(ucpProperties, commerceAdapter, null);
    }

    /**
     * Creates a new {@link UcpDiscoveryController}.
     *
     * @param ucpProperties     the UCP configuration properties
     * @param commerceAdapter   the active commerce adapter
     * @param ap2MandateService optional AP2 mandate service when
     *                          {@code ucp.ap2.enabled=true}
     */
    @Autowired
    public UcpDiscoveryController(
            UcpProperties ucpProperties,
            CommerceAdapter commerceAdapter,
            @Nullable Ap2MandateService ap2MandateService) {
        this.ucpProperties = ucpProperties;
        this.commerceAdapter = commerceAdapter;
        this.ap2MandateService = ap2MandateService;
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

            if (ap2MandateService != null) {
                Map<String, Object> vpFormatsSupported = new LinkedHashMap<>();
                List<String> configuredFormats = ucpProperties.getAp2().getVpFormats();
                if (configuredFormats == null || configuredFormats.isEmpty()) {
                    vpFormatsSupported.put("dc+sd-jwt", Collections.emptyMap());
                } else {
                    for (String format : configuredFormats) {
                        if (hasText(format)) {
                            vpFormatsSupported.put(format.trim(), Collections.emptyMap());
                        }
                    }
                }
                Map<String, Object> ap2Config = Map.of("vp_formats_supported", vpFormatsSupported);
                capabilities.put(AP2_MANDATE_CAPABILITY, List.of(new Capability(
                        UCP_VERSION,
                        BASE_URL + "/specification/payment/extensions/ap2-mandates/",
                        BASE_URL + "/schemas/common/payment_ap2_mandate.json",
                        "dev.ucp.shopping.checkout",
                        ap2Config)));
            }
        }
        if (commerceAdapter.getOrderAdapter() != null) {
            capabilities.put("dev.ucp.shopping.order", List.of(capability(
                    "/specification/shopping/order/", "/schemas/shopping/order.json")));
        }
        if (commerceAdapter.getCustomerAdapter() != null) {
            capabilities.put("dev.ucp.common.identity_linking", List.of(capability(
                    "/specification/common/identity-linking/", "/schemas/common/identity_linking.json")));
        }

        Map<String, String> supportedVersions = resolveSupportedVersions();
        Map<String, List<PaymentHandler>> paymentHandlers = resolvePaymentHandlers();
        List<JwkKey> keys = resolvePublicKeys();

        return new DiscoveryResponse(
                new UcpInfo(UCP_VERSION, supportedVersions, resolveServices(), capabilities, paymentHandlers),
                keys);
    }

    private Map<String, String> resolveSupportedVersions() {
        Map<String, String> configured = ucpProperties.getSupportedVersions();
        if (configured == null || configured.isEmpty()) {
            return null;
        }
        Map<String, String> clean = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configured.entrySet()) {
            if (hasText(entry.getKey()) && hasText(entry.getValue())) {
                clean.put(entry.getKey().trim(), entry.getValue().trim());
            }
        }
        return clean.isEmpty() ? null : Collections.unmodifiableMap(clean);
    }

    private Map<String, List<PaymentHandler>> resolvePaymentHandlers() {
        Map<String, List<UcpProperties.PaymentHandler>> configured = ucpProperties.getPaymentHandlers();
        if (configured == null || configured.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<PaymentHandler>> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, List<UcpProperties.PaymentHandler>> entry : configured.entrySet()) {
            if (!hasText(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            List<PaymentHandler> handlers = new ArrayList<>();
            for (UcpProperties.PaymentHandler ph : entry.getValue()) {
                if (ph == null || !hasText(ph.getId())) {
                    continue;
                }
                String ver = hasText(ph.getVersion()) ? ph.getVersion().trim() : UCP_VERSION;
                List<AvailableInstrument> instruments = null;
                if (ph.getAvailableInstruments() != null && !ph.getAvailableInstruments().isEmpty()) {
                    List<AvailableInstrument> mapped = ph.getAvailableInstruments().stream()
                            .filter(inst -> inst != null && hasText(inst.getType()))
                            .map(inst -> new AvailableInstrument(
                                    inst.getType().trim(),
                                    inst.getConstraints() != null && !inst.getConstraints().isEmpty()
                                            ? Map.copyOf(inst.getConstraints())
                                            : null))
                            .toList();
                    if (!mapped.isEmpty()) {
                        instruments = mapped;
                    }
                }
                Map<String, Object> cfg = ph.getConfig() != null && !ph.getConfig().isEmpty()
                        ? Collections.unmodifiableMap(new LinkedHashMap<>(ph.getConfig()))
                        : null;
                handlers.add(new PaymentHandler(
                        ph.getId().trim(),
                        ver,
                        hasText(ph.getSpec()) ? ph.getSpec().trim() : null,
                        hasText(ph.getSchema()) ? ph.getSchema().trim() : null,
                        instruments,
                        cfg));
            }
            if (!handlers.isEmpty()) {
                resolved.put(entry.getKey().trim(), List.copyOf(handlers));
            }
        }
        return resolved.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(resolved);
    }

    private List<JwkKey> resolvePublicKeys() {
        List<JwkKey> keys = new ArrayList<>();
        Set<String> seenKids = new LinkedHashSet<>();

        if (ucpProperties.getKeys() != null) {
            for (UcpProperties.TrustedKey configuredKey : ucpProperties.getKeys()) {
                if (configuredKey != null && hasText(configuredKey.getKid()) && hasText(configuredKey.getX())) {
                    JwkKey jwk = configuredKey.toJwkKey();
                    if (seenKids.add(jwk.kid())) {
                        keys.add(jwk);
                    }
                }
            }
        }

        if (ap2MandateService != null && commerceAdapter.getCheckoutAdapter() != null) {
            JwkKey ap2Key = ap2MandateService.getMerchantPublicJwk();
            if (seenKids.add(ap2Key.kid())) {
                keys.add(ap2Key);
            }
        }

        return keys.isEmpty() ? null : List.copyOf(keys);
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
     * @param ucp  the protocol information object
     * @param keys optional list of public signing keys (RFC 7517 / RFC 8037 JWKs)
     *             for HTTP Message Signatures, Web Bot Auth, and AP2 Mandates
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record DiscoveryResponse(UcpInfo ucp, List<JwkKey> keys) {

        /**
         * Creates a {@link DiscoveryResponse} without top-level signing keys.
         *
         * @param ucp the protocol information object
         */
        public DiscoveryResponse(UcpInfo ucp) {
            this(ucp, null);
        }
    }

    /**
     * Detailed UCP protocol metadata conforming to
     * {@code ucp.json#/$defs/business_schema}.
     * <p>
     * Both {@code services} and {@code payment_handlers} are always serialized
     * (even when empty {@code {}}), whereas {@code supported_versions} is omitted
     * when empty.
     *
     * @param version           The protocol version (e.g., {@code 2026-08-25}).
     * @param supportedVersions Optional map of older supported protocol versions
     *                          to their profile URIs.
     * @param services          Service registry keyed by reverse-domain name.
     * @param capabilities      Capability registry keyed by reverse-domain name.
     * @param paymentHandlers   Payment handler registry keyed by reverse-domain name.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UcpInfo(
            String version,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            @JsonProperty("supported_versions")
            Map<String, String> supportedVersions,
            Map<String, List<Service>> services,
            Map<String, List<Capability>> capabilities,
            @JsonProperty("payment_handlers")
            Map<String, List<PaymentHandler>> paymentHandlers) {

        /**
         * Creates a {@link UcpInfo} with empty {@code payment_handlers} and no
         * {@code supported_versions}.
         *
         * @param version      protocol version
         * @param services     service registry
         * @param capabilities capability registry
         */
        public UcpInfo(
                String version,
                Map<String, List<Service>> services,
                Map<String, List<Capability>> capabilities) {
            this(version, null, services, capabilities, Collections.emptyMap());
        }
    }

    /**
     * Details about a specific capability or extension version
     * ({@code capability.json}).
     *
     * @param version          The version of the capability spec supported.
     * @param spec             The URL to the human-readable UCP specification.
     * @param schema           The URL to the JSON Schema for this capability.
     * @param id               Optional unique identifier disambiguating multiple instances.
     * @param parentCapability Optional parent capability ({@code String} for single-parent
     *                         or {@code List<String>} for multi-parent extensions,
     *                         serialized as {@code extends}).
     * @param config           Optional capability-specific configuration map.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Capability(
            String version,
            String spec,
            String schema,
            String id,
            @JsonProperty("extends") Object parentCapability,
            Map<String, Object> config) {

        /**
         * Creates a root capability entry without {@code id}, {@code extends}, or {@code config}.
         *
         * @param version The version of the capability spec supported.
         * @param spec    The URL to the official UCP specification for this capability.
         * @param schema  The URL to the JSON Schema for this capability.
         */
        public Capability(String version, String spec, String schema) {
            this(version, spec, schema, null, null, null);
        }

        /**
         * Creates a single-parent extension capability entry.
         *
         * @param version          The version of the capability spec supported.
         * @param spec             The URL to the official UCP specification.
         * @param schema           The URL to the JSON Schema.
         * @param parentCapability Single parent capability name.
         * @param config           Optional capability-specific configuration map.
         */
        public Capability(
                String version,
                String spec,
                String schema,
                String parentCapability,
                Map<String, Object> config) {
            this(version, spec, schema, null, (Object) parentCapability, config);
        }

        /**
         * Creates a multi-parent extension capability entry (e.g., an extension
         * augmenting both {@code dev.ucp.shopping.checkout} and
         * {@code dev.ucp.shopping.cart}).
         *
         * @param version            The version of the capability spec supported.
         * @param spec               The URL to the official UCP specification.
         * @param schema             The URL to the JSON Schema.
         * @param parentCapabilities List of parent capability names.
         * @param config             Optional capability-specific configuration map.
         */
        public Capability(
                String version,
                String spec,
                String schema,
                List<String> parentCapabilities,
                Map<String, Object> config) {
            this(version, spec, schema, null,
                    parentCapabilities != null && parentCapabilities.size() == 1
                            ? parentCapabilities.get(0)
                            : (parentCapabilities != null ? List.copyOf(parentCapabilities) : null),
                    config);
        }
    }

    /**
     * Details about a service transport binding ({@code service.json}).
     *
     * @param version   The version of the service spec supported.
     * @param spec      The URL to the official UCP specification for this service.
     * @param transport The transport protocol ({@code rest}, {@code mcp},
     *                  {@code a2a} or {@code embedded}).
     * @param endpoint  The endpoint URL for this transport binding.
     * @param schema    The URL to the schema describing this transport binding.
     * @param id        Optional unique identifier disambiguating multiple instances.
     * @param config    Optional transport-specific configuration map.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Service(
            String version,
            String spec,
            String transport,
            String endpoint,
            String schema,
            String id,
            Map<String, Object> config) {

        /**
         * Creates a service binding without {@code id} or {@code config}.
         *
         * @param version   service version
         * @param spec      specification URL
         * @param transport transport protocol
         * @param endpoint  transport endpoint URL
         * @param schema    transport schema URL
         */
        public Service(String version, String spec, String transport, String endpoint, String schema) {
            this(version, spec, transport, endpoint, schema, null, null);
        }
    }

    /**
     * Payment handler declaration ({@code payment_handler.json}).
     *
     * @param id                   Unique identifier for this payment handler instance.
     * @param version              Entity version in {@code YYYY-MM-DD} format.
     * @param spec                 Optional URL to human-readable handler specification.
     * @param schema               Optional URL to handler JSON Schema.
     * @param availableInstruments Optional list of supported instrument types and constraints.
     * @param config               Optional handler-specific discovery configuration.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentHandler(
            String id,
            String version,
            String spec,
            String schema,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            @JsonProperty("available_instruments")
            List<AvailableInstrument> availableInstruments,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            Map<String, Object> config) {
    }

    /**
     * Available payment instrument entry ({@code available_payment_instrument.json}).
     *
     * @param type        Instrument type identifier (e.g., {@code card}, {@code shop_pay}).
     * @param constraints Optional constraint expression map.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AvailableInstrument(
            String type,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            Map<String, Object> constraints) {
    }
}
