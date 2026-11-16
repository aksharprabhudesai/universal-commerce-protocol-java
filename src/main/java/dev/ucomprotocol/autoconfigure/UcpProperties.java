package dev.ucomprotocol.autoconfigure;

import dev.ucomprotocol.ap2.JwkKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the Universal Commerce Protocol library.
 * <p>
 * Mapped to the {@code ucp} prefix in application configuration (e.g.,
 * application.properties).
 */
@ConfigurationProperties(prefix = "ucp")
public class UcpProperties {
    /**
     * The commerce provider to use (e.g., mock, shopify).
     */
    private String provider = "mock";

    /**
     * API key for the commerce provider.
     */
    private String apiKey;

    /**
     * API secret or password for the commerce provider.
     */
    private String apiSecret;

    /**
     * Endpoint URL for the commerce provider.
     */
    private String endpoint;

    /**
     * Optional map of older UCP protocol versions supported by this business to
     * their self-contained profile URIs (serialized as
     * {@code ucp.supported_versions} at {@code /.well-known/ucp}).
     */
    private Map<String, String> supportedVersions = new LinkedHashMap<>();

    /**
     * Payment handler declarations keyed by reverse-domain handler name
     * (serialized as required {@code ucp.payment_handlers} at
     * {@code /.well-known/ucp}).
     */
    private Map<String, List<PaymentHandler>> paymentHandlers = new LinkedHashMap<>();

    /**
     * Additional public signing keys ({@code EC} or {@code OKP}/{@code Ed25519})
     * published in top-level {@code keys[]} at {@code /.well-known/ucp} (e.g.,
     * for RFC 9421 HTTP Message Signatures and Web Bot Auth interop).
     */
    private List<TrustedKey> keys = new ArrayList<>();

    /**
     * Transport endpoints advertised for the {@code dev.ucp.shopping} service
     * in the discovery profile.
     */
    private final Services services = new Services();

    /**
     * Resilience4j circuit-breaker and retry decoration settings.
     */
    private final Resilience resilience = new Resilience();

    /**
     * AP2 Mandates cryptographic extension settings
     * ({@code dev.ucp.common.payment.ap2_mandate}).
     */
    private final Ap2 ap2 = new Ap2();

    /**
     * Returns the configured commerce provider name.
     *
     * @return the provider name
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Sets the commerce provider name.
     *
     * @param provider the provider name
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider API key.
     *
     * @return the API key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Sets the provider API key.
     *
     * @param apiKey the API key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Returns the provider API secret.
     *
     * @return the API secret
     */
    public String getApiSecret() {
        return apiSecret;
    }

    /**
     * Sets the provider API secret.
     *
     * @param apiSecret the API secret
     */
    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    /**
     * Returns the provider backend endpoint URL.
     *
     * @return the endpoint URL
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Sets the provider backend endpoint URL.
     *
     * @param endpoint the endpoint URL
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Returns the map of older supported UCP protocol versions to profile URIs.
     *
     * @return supported versions map
     */
    public Map<String, String> getSupportedVersions() {
        return supportedVersions;
    }

    /**
     * Sets the map of older supported UCP protocol versions to profile URIs.
     *
     * @param supportedVersions supported versions map
     */
    public void setSupportedVersions(Map<String, String> supportedVersions) {
        this.supportedVersions = supportedVersions;
    }

    /**
     * Returns the configured payment handler registry entries.
     *
     * @return payment handlers keyed by reverse-domain name
     */
    public Map<String, List<PaymentHandler>> getPaymentHandlers() {
        return paymentHandlers;
    }

    /**
     * Sets the configured payment handler registry entries.
     *
     * @param paymentHandlers payment handlers keyed by reverse-domain name
     */
    public void setPaymentHandlers(Map<String, List<PaymentHandler>> paymentHandlers) {
        this.paymentHandlers = paymentHandlers;
    }

    /**
     * Returns the additional public signing keys published in top-level
     * {@code keys[]} at {@code /.well-known/ucp}.
     *
     * @return list of public signing keys
     */
    public List<TrustedKey> getKeys() {
        return keys;
    }

    /**
     * Sets the additional public signing keys published in top-level
     * {@code keys[]} at {@code /.well-known/ucp}.
     *
     * @param keys list of public signing keys
     */
    public void setKeys(List<TrustedKey> keys) {
        this.keys = keys;
    }

    /**
     * Returns the discovery transport service settings.
     *
     * @return the services configuration
     */
    public Services getServices() {
        return services;
    }

    /**
     * Returns the resilience configuration settings.
     *
     * @return the resilience configuration
     */
    public Resilience getResilience() {
        return resilience;
    }

    /**
     * Returns the AP2 Mandates configuration settings.
     *
     * @return the AP2 configuration
     */
    public Ap2 getAp2() {
        return ap2;
    }

    /**
     * Transport endpoints for the UCP shopping service. Only configured
     * endpoints are advertised at {@code /.well-known/ucp}.
     */
    public static class Services {
        /**
         * Endpoint URL of the REST transport binding (e.g.,
         * https://shop.example.com/ucp/v1).
         */
        private String restEndpoint;

        /**
         * Endpoint URL of the MCP transport binding (e.g.,
         * https://shop.example.com/ucp/mcp).
         */
        private String mcpEndpoint;

        /**
         * URL of the A2A agent card (e.g.,
         * https://shop.example.com/.well-known/agent-card.json).
         */
        private String a2aEndpoint;

        /**
         * Whether to advertise the embedded OpenRPC shopping transport binding.
         */
        private boolean embeddedEnabled = false;

        /**
         * Returns the REST transport endpoint URL.
         *
         * @return the REST endpoint URL
         */
        public String getRestEndpoint() {
            return restEndpoint;
        }

        /**
         * Sets the REST transport endpoint URL.
         *
         * @param restEndpoint the REST endpoint URL
         */
        public void setRestEndpoint(String restEndpoint) {
            this.restEndpoint = restEndpoint;
        }

        /**
         * Returns the MCP transport endpoint URL.
         *
         * @return the MCP endpoint URL
         */
        public String getMcpEndpoint() {
            return mcpEndpoint;
        }

        /**
         * Sets the MCP transport endpoint URL.
         *
         * @param mcpEndpoint the MCP endpoint URL
         */
        public void setMcpEndpoint(String mcpEndpoint) {
            this.mcpEndpoint = mcpEndpoint;
        }

        /**
         * Returns the A2A agent card URL.
         *
         * @return the A2A endpoint URL
         */
        public String getA2aEndpoint() {
            return a2aEndpoint;
        }

        /**
         * Sets the A2A agent card URL.
         *
         * @param a2aEndpoint the A2A endpoint URL
         */
        public void setA2aEndpoint(String a2aEndpoint) {
            this.a2aEndpoint = a2aEndpoint;
        }

        /**
         * Returns whether the embedded OpenRPC transport binding is advertised.
         *
         * @return {@code true} if embedded transport is enabled
         */
        public boolean isEmbeddedEnabled() {
            return embeddedEnabled;
        }

        /**
         * Sets whether to advertise the embedded OpenRPC transport binding.
         *
         * @param embeddedEnabled {@code true} to advertise embedded transport
         */
        public void setEmbeddedEnabled(boolean embeddedEnabled) {
            this.embeddedEnabled = embeddedEnabled;
        }
    }

    /**
     * Resilience4j decoration settings for {@code CommerceAdapter} beans.
     */
    public static class Resilience {
        /**
         * Whether to automatically wrap {@code CommerceAdapter} beans with
         * Resilience4j circuit breaker and retry decorators when Resilience4j
         * registries are present in the application context.
         */
        private boolean enabled = true;

        /**
         * Name of the Resilience4j CircuitBreaker and Retry instance to use.
         */
        private String instanceName = "ucp";

        /**
         * Returns whether Resilience4j decoration is enabled.
         *
         * @return {@code true} if enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether Resilience4j decoration is enabled.
         *
         * @param enabled {@code true} to enable decoration
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the Resilience4j instance name used for circuit breaker and retry
         * lookups.
         *
         * @return the instance name
         */
        public String getInstanceName() {
            return instanceName;
        }

        /**
         * Sets the Resilience4j instance name used for circuit breaker and retry
         * lookups.
         *
         * @param instanceName the instance name
         */
        public void setInstanceName(String instanceName) {
            this.instanceName = instanceName;
        }
    }

    /**
     * Configuration settings for the UCP AP2 Mandates extension
     * ({@code dev.ucp.common.payment.ap2_mandate}).
     */
    public static class Ap2 {
        /**
         * Whether to enable AP2 Mandate checkout signing, mandate verification,
         * and {@code /.well-known/ucp} key publication.
         */
        private boolean enabled = false;

        /**
         * Key identifier ({@code kid}) published in {@code /.well-known/ucp}
         * {@code keys[]} and detached JWS headers.
         */
        private String keyId = "merchant-ap2-key-1";

        /**
         * JWA signature algorithm ({@code ES256}, {@code ES384}, {@code ES512},
         * or {@code EdDSA}).
         */
        private String algorithm = "ES256";

        /**
         * Optional PKCS#8 PEM or Base64-encoded private key for merchant signing.
         */
        private String signingPrivateKey;

        /**
         * Optional X.509 PEM or Base64-encoded public key corresponding to
         * {@code signingPrivateKey}.
         */
        private String signingPublicKey;

        /**
         * Whether to automatically generate an in-memory ephemeral key pair on
         * startup when {@code signingPrivateKey} is omitted.
         */
        private boolean autoGenerateEphemeralKey = true;

        /**
         * Whether {@code completeCheckout} strictly requires a valid
         * {@code ap2.checkout_mandate}.
         */
        private boolean requireCheckoutMandate = true;

        /**
         * Whether to cryptographically verify JWS signatures on submitted
         * {@code ap2.checkout_mandate} credentials.
         */
        private boolean verifyMandateSignature = true;

        /**
         * Whether to accept self-contained agent JWKs embedded in mandate JWS
         * headers or {@code cnf.jwk} claims when {@code kid} is not pre-registered
         * in {@code trustedPlatformKeys}. Useful for sandboxes and open agent tests.
         */
        private boolean allowEmbeddedAgentJwk = false;

        /**
         * Verifiable presentation formats advertised under
         * {@code config.vp_formats_supported} in {@code /.well-known/ucp}.
         */
        private List<String> vpFormats = new ArrayList<>(List.of("dc+sd-jwt"));

        /**
         * Pre-trusted platform or agent public JWKs ({@code EC} or
         * {@code OKP}/{@code Ed25519}) allowed to sign {@code ap2.checkout_mandate}
         * credentials.
         */
        private List<TrustedKey> trustedPlatformKeys = new ArrayList<>();

        /**
         * Returns whether AP2 Mandates support is enabled.
         *
         * @return {@code true} if enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether AP2 Mandates support is enabled.
         *
         * @param enabled {@code true} to enable AP2
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the merchant signing key ID ({@code kid}).
         *
         * @return the key ID
         */
        public String getKeyId() {
            return keyId;
        }

        /**
         * Sets the merchant signing key ID ({@code kid}).
         *
         * @param keyId the key ID
         */
        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        /**
         * Returns the configured JWA signature algorithm.
         *
         * @return the algorithm ({@code ES256}, {@code ES384}, {@code ES512}, or {@code EdDSA})
         */
        public String getAlgorithm() {
            return algorithm;
        }

        /**
         * Sets the JWA signature algorithm.
         *
         * @param algorithm the algorithm name
         */
        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        /**
         * Returns the configured PKCS#8 private key PEM or Base64 string.
         *
         * @return the private key text, or {@code null}
         */
        public String getSigningPrivateKey() {
            return signingPrivateKey;
        }

        /**
         * Sets the PKCS#8 private key PEM or Base64 string.
         *
         * @param signingPrivateKey the private key text
         */
        public void setSigningPrivateKey(String signingPrivateKey) {
            this.signingPrivateKey = signingPrivateKey;
        }

        /**
         * Returns the configured X.509 public key PEM or Base64 string.
         *
         * @return the public key text, or {@code null}
         */
        public String getSigningPublicKey() {
            return signingPublicKey;
        }

        /**
         * Sets the X.509 public key PEM or Base64 string.
         *
         * @param signingPublicKey the public key text
         */
        public void setSigningPublicKey(String signingPublicKey) {
            this.signingPublicKey = signingPublicKey;
        }

        /**
         * Returns whether an ephemeral key pair is generated when no explicit
         * key pair is configured.
         *
         * @return {@code true} if ephemeral key generation is enabled
         */
        public boolean isAutoGenerateEphemeralKey() {
            return autoGenerateEphemeralKey;
        }

        /**
         * Sets whether to generate an ephemeral key pair when no explicit key
         * pair is configured.
         *
         * @param autoGenerateEphemeralKey {@code true} to allow ephemeral keys
         */
        public void setAutoGenerateEphemeralKey(boolean autoGenerateEphemeralKey) {
            this.autoGenerateEphemeralKey = autoGenerateEphemeralKey;
        }

        /**
         * Returns whether {@code completeCheckout} requires a valid mandate.
         *
         * @return {@code true} if mandates are strictly required
         */
        public boolean isRequireCheckoutMandate() {
            return requireCheckoutMandate;
        }

        /**
         * Sets whether {@code completeCheckout} requires a valid mandate.
         *
         * @param requireCheckoutMandate {@code true} to require mandates
         */
        public void setRequireCheckoutMandate(boolean requireCheckoutMandate) {
            this.requireCheckoutMandate = requireCheckoutMandate;
        }

        /**
         * Returns whether cryptographic signature verification is enabled for
         * checkout mandates.
         *
         * @return {@code true} if signature verification is enabled
         */
        public boolean isVerifyMandateSignature() {
            return verifyMandateSignature;
        }

        /**
         * Sets whether cryptographic signature verification is enabled for checkout
         * mandates.
         *
         * @param verifyMandateSignature {@code true} to verify signatures
         */
        public void setVerifyMandateSignature(boolean verifyMandateSignature) {
            this.verifyMandateSignature = verifyMandateSignature;
        }

        /**
         * Returns whether embedded agent JWKs in mandates are accepted.
         *
         * @return {@code true} if embedded agent JWKs are accepted
         */
        public boolean isAllowEmbeddedAgentJwk() {
            return allowEmbeddedAgentJwk;
        }

        /**
         * Sets whether embedded agent JWKs in mandates are accepted.
         *
         * @param allowEmbeddedAgentJwk {@code true} to accept embedded JWKs
         */
        public void setAllowEmbeddedAgentJwk(boolean allowEmbeddedAgentJwk) {
            this.allowEmbeddedAgentJwk = allowEmbeddedAgentJwk;
        }

        /**
         * Returns the supported Verifiable Presentation format identifiers.
         *
         * @return list of VP format identifiers (e.g., {@code dc+sd-jwt})
         */
        public List<String> getVpFormats() {
            return vpFormats;
        }

        /**
         * Sets the supported Verifiable Presentation format identifiers.
         *
         * @param vpFormats list of VP format identifiers
         */
        public void setVpFormats(List<String> vpFormats) {
            this.vpFormats = vpFormats;
        }

        /**
         * Returns the configured list of trusted platform public JWKs.
         *
         * @return list of trusted keys
         */
        public List<TrustedKey> getTrustedPlatformKeys() {
            return trustedPlatformKeys;
        }

        /**
         * Sets the configured list of trusted platform public JWKs.
         *
         * @param trustedPlatformKeys list of trusted keys
         */
        public void setTrustedPlatformKeys(List<TrustedKey> trustedPlatformKeys) {
            this.trustedPlatformKeys = trustedPlatformKeys;
        }
    }

    /**
     * Bindable configuration POJO representing a UCP {@code payment_handler.json}
     * entry in {@code ucp.payment_handlers}.
     */
    public static class PaymentHandler {
        private String id;
        private String version = "2026-08-25";
        private String spec;
        private String schema;
        private List<AvailableInstrument> availableInstruments = new ArrayList<>();
        private Map<String, Object> config = new LinkedHashMap<>();

        /**
         * Returns the unique payment handler instance identifier.
         *
         * @return handler ID
         */
        public String getId() {
            return id;
        }

        /**
         * Sets the unique payment handler instance identifier.
         *
         * @param id handler ID
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Returns the payment handler specification version ({@code YYYY-MM-DD}).
         *
         * @return version string
         */
        public String getVersion() {
            return version;
        }

        /**
         * Sets the payment handler specification version ({@code YYYY-MM-DD}).
         *
         * @param version version string
         */
        public void setVersion(String version) {
            this.version = version;
        }

        /**
         * Returns the human-readable specification URL.
         *
         * @return specification URL
         */
        public String getSpec() {
            return spec;
        }

        /**
         * Sets the human-readable specification URL.
         *
         * @param spec specification URL
         */
        public void setSpec(String spec) {
            this.spec = spec;
        }

        /**
         * Returns the JSON Schema URL for this payment handler.
         *
         * @return schema URL
         */
        public String getSchema() {
            return schema;
        }

        /**
         * Sets the JSON Schema URL for this payment handler.
         *
         * @param schema schema URL
         */
        public void setSchema(String schema) {
            this.schema = schema;
        }

        /**
         * Returns the available payment instrument types and constraints.
         *
         * @return list of available instruments
         */
        public List<AvailableInstrument> getAvailableInstruments() {
            return availableInstruments;
        }

        /**
         * Sets the available payment instrument types and constraints.
         *
         * @param availableInstruments list of available instruments
         */
        public void setAvailableInstruments(List<AvailableInstrument> availableInstruments) {
            this.availableInstruments = availableInstruments;
        }

        /**
         * Returns the handler-specific discovery configuration map.
         *
         * @return configuration map
         */
        public Map<String, Object> getConfig() {
            return config;
        }

        /**
         * Sets the handler-specific discovery configuration map.
         *
         * @param config configuration map
         */
        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }

    /**
     * Bindable configuration POJO representing an
     * {@code available_payment_instrument.json} entry on a {@link PaymentHandler}.
     */
    public static class AvailableInstrument {
        private String type;
        private Map<String, Object> constraints = new LinkedHashMap<>();

        /**
         * Returns the payment instrument type (e.g., {@code card}, {@code shop_pay}).
         *
         * @return instrument type
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the payment instrument type.
         *
         * @param type instrument type
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Returns optional constraint expressions for this instrument type.
         *
         * @return constraints map
         */
        public Map<String, Object> getConstraints() {
            return constraints;
        }

        /**
         * Sets optional constraint expressions for this instrument type.
         *
         * @param constraints constraints map
         */
        public void setConstraints(Map<String, Object> constraints) {
            this.constraints = constraints;
        }
    }

    /**
     * Bindable configuration POJO representing a public JWK ({@code EC} or
     * {@code OKP}/{@code Ed25519}).
     */
    public static class TrustedKey {
        private String kid;
        private String kty = "EC";
        private String crv = "P-256";
        private String x;
        private String y;
        private String use = "sig";
        private String alg = "ES256";

        /**
         * Converts this bound property entry into an immutable {@link JwkKey}.
         *
         * @return {@link JwkKey} record
         */
        public JwkKey toJwkKey() {
            String effectiveY = "OKP".equalsIgnoreCase(kty) ? null : y;
            return new JwkKey(kid, kty, crv, x, effectiveY, use, alg);
        }

        /**
         * Returns the key identifier ({@code kid}).
         *
         * @return key identifier
         */
        public String getKid() {
            return kid;
        }

        /**
         * Sets the key identifier ({@code kid}).
         *
         * @param kid key identifier
         */
        public void setKid(String kid) {
            this.kid = kid;
        }

        /**
         * Returns the JWK key type ({@code EC} or {@code OKP}).
         *
         * @return key type
         */
        public String getKty() {
            return kty;
        }

        /**
         * Sets the JWK key type ({@code EC} or {@code OKP}).
         *
         * @param kty key type
         */
        public void setKty(String kty) {
            this.kty = kty;
        }

        /**
         * Returns the curve name ({@code P-256}, {@code P-384}, {@code P-521}, or
         * {@code Ed25519}).
         *
         * @return curve name
         */
        public String getCrv() {
            return crv;
        }

        /**
         * Sets the curve name.
         *
         * @param crv curve name
         */
        public void setCrv(String crv) {
            this.crv = crv;
        }

        /**
         * Returns the base64url-encoded X coordinate (or 32-byte Ed25519 public key).
         *
         * @return X coordinate
         */
        public String getX() {
            return x;
        }

        /**
         * Sets the base64url-encoded X coordinate.
         *
         * @param x X coordinate
         */
        public void setX(String x) {
            this.x = x;
        }

        /**
         * Returns the base64url-encoded Y coordinate ({@code null} for {@code OKP}).
         *
         * @return Y coordinate
         */
        public String getY() {
            return y;
        }

        /**
         * Sets the base64url-encoded Y coordinate.
         *
         * @param y Y coordinate
         */
        public void setY(String y) {
            this.y = y;
        }

        /**
         * Returns the intended public key use ({@code use}).
         *
         * @return key use
         */
        public String getUse() {
            return use;
        }

        /**
         * Sets the intended public key use ({@code use}).
         *
         * @param use key use
         */
        public void setUse(String use) {
            this.use = use;
        }

        /**
         * Returns the JWA signature algorithm ({@code ES256}, {@code ES384},
         * {@code ES512}, or {@code EdDSA}).
         *
         * @return algorithm name
         */
        public String getAlg() {
            return alg;
        }

        /**
         * Sets the JWA signature algorithm.
         *
         * @param alg algorithm name
         */
        public void setAlg(String alg) {
            this.alg = alg;
        }
    }
}
