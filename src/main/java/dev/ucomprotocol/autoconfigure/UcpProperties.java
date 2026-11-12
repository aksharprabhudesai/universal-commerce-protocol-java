package dev.ucomprotocol.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
