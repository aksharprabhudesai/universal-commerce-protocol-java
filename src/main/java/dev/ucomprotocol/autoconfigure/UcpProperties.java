package dev.ucomprotocol.autoconfigure;

import dev.ucomprotocol.ap2.JwkKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
         * JWS ECDSA algorithm ({@code ES256}, {@code ES384}, or {@code ES512}).
         */
        private String algorithm = "ES256";

        /**
         * Optional PKCS#8 PEM or Base64-encoded EC private key for merchant
         * signing.
         */
        private String signingPrivateKey;

        /**
         * Optional X.509 PEM or Base64-encoded EC public key corresponding to
         * {@code signingPrivateKey}.
         */
        private String signingPublicKey;

        /**
         * Whether to automatically generate an in-memory ephemeral EC key pair on
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
         * Pre-trusted platform or agent public EC JWKs allowed to sign
         * {@code ap2.checkout_mandate} credentials.
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
         * Returns the configured JWS ECDSA algorithm.
         *
         * @return the algorithm ({@code ES256}, {@code ES384}, or {@code ES512})
         */
        public String getAlgorithm() {
            return algorithm;
        }

        /**
         * Sets the JWS ECDSA algorithm.
         *
         * @param algorithm the algorithm name
         */
        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        /**
         * Returns the configured PKCS#8 EC private key PEM or Base64 string.
         *
         * @return the private key text, or {@code null}
         */
        public String getSigningPrivateKey() {
            return signingPrivateKey;
        }

        /**
         * Sets the PKCS#8 EC private key PEM or Base64 string.
         *
         * @param signingPrivateKey the private key text
         */
        public void setSigningPrivateKey(String signingPrivateKey) {
            this.signingPrivateKey = signingPrivateKey;
        }

        /**
         * Returns the configured X.509 EC public key PEM or Base64 string.
         *
         * @return the public key text, or {@code null}
         */
        public String getSigningPublicKey() {
            return signingPublicKey;
        }

        /**
         * Sets the X.509 EC public key PEM or Base64 string.
         *
         * @param signingPublicKey the public key text
         */
        public void setSigningPublicKey(String signingPublicKey) {
            this.signingPublicKey = signingPublicKey;
        }

        /**
         * Returns whether an ephemeral EC key pair is generated when no explicit
         * key pair is configured.
         *
         * @return {@code true} if ephemeral key generation is enabled
         */
        public boolean isAutoGenerateEphemeralKey() {
            return autoGenerateEphemeralKey;
        }

        /**
         * Sets whether to generate an ephemeral EC key pair when no explicit key
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
         * Returns the configured list of trusted platform public EC keys.
         *
         * @return list of trusted keys
         */
        public List<TrustedKey> getTrustedPlatformKeys() {
            return trustedPlatformKeys;
        }

        /**
         * Sets the configured list of trusted platform public EC keys.
         *
         * @param trustedPlatformKeys list of trusted keys
         */
        public void setTrustedPlatformKeys(List<TrustedKey> trustedPlatformKeys) {
            this.trustedPlatformKeys = trustedPlatformKeys;
        }
    }

    /**
     * Bindable configuration POJO representing a trusted platform EC public JWK.
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
            return new JwkKey(kid, kty, crv, x, y, use, alg);
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
         * Returns the JWK key type ({@code kty}).
         *
         * @return key type
         */
        public String getKty() {
            return kty;
        }

        /**
         * Sets the JWK key type ({@code kty}).
         *
         * @param kty key type
         */
        public void setKty(String kty) {
            this.kty = kty;
        }

        /**
         * Returns the EC curve name ({@code crv}).
         *
         * @return curve name
         */
        public String getCrv() {
            return crv;
        }

        /**
         * Sets the EC curve name ({@code crv}).
         *
         * @param crv curve name
         */
        public void setCrv(String crv) {
            this.crv = crv;
        }

        /**
         * Returns the base64url-encoded X coordinate.
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
         * Returns the base64url-encoded Y coordinate.
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
         * Returns the JWS algorithm ({@code alg}).
         *
         * @return algorithm name
         */
        public String getAlg() {
            return alg;
        }

        /**
         * Sets the JWS algorithm ({@code alg}).
         *
         * @param alg algorithm name
         */
        public void setAlg(String alg) {
            this.alg = alg;
        }
    }
}
