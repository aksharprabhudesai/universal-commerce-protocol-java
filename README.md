# UCP Java - Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/dev.ucomprotocol/ucp-spring-boot-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.ucomprotocol/ucp-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

The **Universal Commerce Protocol (UCP)** library is a standardized, protocol-agnostic framework designed for Spring Boot. It abstracts the complexities of integrating with multiple commerce platforms (Shopify, Magento, Commercetools, etc.) into a single, unified interface.

* **Website**: [ucomprotocol.dev](https://ucomprotocol.dev)
* **Official Specification**: [ucp.dev](https://ucp.dev) (Version `2026-08-25`)
* **Javadoc Reference**: [ucomprotocol.dev/javadocs](https://ucomprotocol.dev/javadocs/)
* **Agentic Protocols Portal**: [agenticprotocols.dev/ucp.html](https://agenticprotocols.dev/ucp.html)

---

## Features

* **Unified Domain Models:** Standardized POJOs for `Product`, `Cart`, `Checkout`, `Order`, and `Customer`.
* **Provider Agnostic:** Switch commerce providers via configuration without touching business logic.
* **Configuration-Driven AP2 Mandates:** Turn on cryptographic checkout signing (`ap2.merchant_authorization`) and verifiable `SD-JWT+kb` mandate enforcement (`ap2.checkout_mandate`) with `ucp.ap2.enabled=true`.
* **Spring Boot Auto-Configuration:** Automatically registers the active `CommerceAdapter` and IDE configuration metadata (`spring-configuration-metadata.json`).
* **Discovery Endpoint:** Exposes protocol metadata and public EC signing keys (`keys[]`) at `/.well-known/ucp` conforming to UCP specification `2026-08-25` when Spring Web MVC is on the classpath.
* **Multi-Transport Support:** Advertise REST, MCP (Model Context Protocol), A2A (Agent-to-Agent), and Embedded OpenRPC endpoints.
* **Resilience Built-in:** Automatic circuit breaking and retries around `CommerceAdapter` operations via `ResilientCommerceAdapter` and Resilience4j.

---

## Getting Started

### Prerequisites
* **Java 17+**
* **Spring Boot 4.x** (use version `0.0.1` for Spring Boot 3.x)

### Installation

#### Gradle (Groovy)
```groovy
dependencies {
    implementation 'dev.ucomprotocol:ucp-spring-boot-starter:0.3.0'
}
```

#### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("dev.ucomprotocol:ucp-spring-boot-starter:0.3.0")
}
```

#### Maven
```xml
<dependency>
    <groupId>dev.ucomprotocol</groupId>
    <artifactId>ucp-spring-boot-starter</artifactId>
    <version>0.3.0</version>
</dependency>
```

---

## Usage

Inject the `CommerceAdapter` bean into your Spring services:

```java
@Service
public class StoreService {

    private final CommerceAdapter commerceAdapter;

    public StoreService(CommerceAdapter commerceAdapter) {
        this.commerceAdapter = commerceAdapter;
    }

    public Product findProduct(String id) {
        return commerceAdapter.getCatalogAdapter()
                .getProductById(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found"));
    }

    public Checkout startCheckout(Cart cart) {
        return commerceAdapter.getCheckoutAdapter()
                .createCheckout(cart);
    }
}
```

---

## Configuration-Driven AP2 Mandates (`dev.ucp.common.payment.ap2_mandate`)

Enable cryptographic **AP2 Mandates** directly via `application.properties`—zero adapter code changes required:

```properties
ucp.ap2.enabled=true
ucp.ap2.key-id=merchant-ap2-2026
ucp.ap2.algorithm=ES256
ucp.ap2.require-checkout-mandate=true

# Optional for production (ephemeral EC key pair is generated if omitted):
# ucp.ap2.auto-generate-ephemeral-key=false
# ucp.ap2.signing-private-key=-----BEGIN PRIVATE KEY-----...
# ucp.ap2.signing-public-key=-----BEGIN PUBLIC KEY-----...

# Pre-trusted platform/agent JWKs (or set ucp.ap2.allow-embedded-agent-jwk=true for sandboxes):
# ucp.ap2.trusted-platform-keys[0].kid=platform-key-1
# ucp.ap2.trusted-platform-keys[0].crv=P-256
# ucp.ap2.trusted-platform-keys[0].x=...
# ucp.ap2.trusted-platform-keys[0].y=...
```

When `ucp.ap2.enabled=true`, UCP Java automatically:
1. Publishes `dev.ucp.common.payment.ap2_mandate` (`extends: dev.ucp.shopping.checkout`) and your merchant EC public JWK in top-level `keys[]` at `/.well-known/ucp`.
2. Wraps `CommerceAdapter` in `Ap2CommerceAdapter`, computing RFC 8785 JCS canonicalization and attaching a detached JWS (`ap2.merchant_authorization`) to every `Checkout`.
3. Verifies incoming `SD-JWT+kb` (`dc+sd-jwt`) `ap2.checkout_mandate` credentials on `completeCheckout(checkoutId, ap2)`—raising `Ap2MandateException` with canonical UCP error codes (`mandate_required`, `mandate_expired`, `mandate_invalid_signature`, `agent_missing_key`, `mandate_scope_mismatch`, `merchant_authorization_invalid`, `merchant_authorization_missing`).

---

## Discovery Endpoint Configuration

UCP Java provides an **Automatic Discovery Endpoint** at `/.well-known/ucp`.

### How to Enable

Include `spring-boot-starter-webmvc` alongside UCP Java:

```groovy
dependencies {
    implementation 'dev.ucomprotocol:ucp-spring-boot-starter:0.3.0'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
}
```

### Advertising Transport Services (Optional)

```properties
ucp.services.rest-endpoint=https://shop.example.com/ucp/v1
ucp.services.mcp-endpoint=https://shop.example.com/ucp/mcp
ucp.services.a2a-endpoint=https://shop.example.com/.well-known/agent-card.json
ucp.services.embedded-enabled=true
```

---

## Configuration Properties Reference

| Property | Default | Description |
|---|---|---|
| `ucp.provider` | `mock` | Active commerce provider implementation (`mock`, `shopify`, custom). |
| `ucp.endpoint` | - | Backend API endpoint URL for the provider. |
| `ucp.api-key` | - | API key for authenticating with the commerce provider. |
| `ucp.api-secret` | - | API secret or password for the commerce provider. |
| `ucp.services.rest-endpoint` | - | Advertised REST transport endpoint for `dev.ucp.shopping`. |
| `ucp.services.mcp-endpoint` | - | Advertised Model Context Protocol (MCP) endpoint. |
| `ucp.services.a2a-endpoint` | - | Advertised Agent-to-Agent (A2A) agent card URL. |
| `ucp.services.embedded-enabled` | `false` | Advertise the embedded OpenRPC shopping transport binding. |
| `ucp.resilience.enabled` | `true` | Automatically decorate `CommerceAdapter` beans with Resilience4j circuit breaker and retry. |
| `ucp.resilience.instance-name` | `ucp` | Resilience4j `CircuitBreaker` and `Retry` instance name. |
| `ucp.ap2.enabled` | `false` | Enable AP2 Mandates checkout signing, verification, and `/.well-known/ucp` `keys[]`. |
| `ucp.ap2.key-id` | `merchant-ap2-key-1` | Merchant signing JWK `kid`. |
| `ucp.ap2.algorithm` | `ES256` | JWS ECDSA curve/algorithm (`ES256`, `ES384`, `ES512`). |
| `ucp.ap2.signing-private-key` | - | PKCS#8 PEM or Base64 EC private key. |
| `ucp.ap2.signing-public-key` | - | X.509 PEM or Base64 EC public key. |
| `ucp.ap2.auto-generate-ephemeral-key` | `true` | Auto-generate an in-memory EC key pair when explicit keys are omitted. |
| `ucp.ap2.require-checkout-mandate` | `true` | Reject `completeCheckout` without `ap2.checkout_mandate` (`mandate_required`). |
| `ucp.ap2.verify-mandate-signature` | `true` | Cryptographically verify `SD-JWT+kb` signatures on `ap2.checkout_mandate`. |
| `ucp.ap2.allow-embedded-agent-jwk` | `false` | Accept self-contained agent JWKs in mandate headers/`cnf.jwk` for sandboxes. |
| `ucp.ap2.vp-formats` | `["dc+sd-jwt"]` | Verifiable Presentation formats advertised in `vp_formats_supported`. |
| `ucp.ap2.trusted-platform-keys` | `[]` | Pre-trusted platform/agent EC public JWKs (`kid`, `crv`, `x`, `y`, `alg`). |

---

## Custom Adapters

To integrate a custom commerce provider, implement the `CommerceAdapter` interface and declare it as a Spring `@Bean`. The auto-configuration automatically backs off from the mock adapter and wraps your adapter with Resilience4j protection (and `Ap2CommerceAdapter` when `ucp.ap2.enabled=true`):

```java
@Configuration
public class MyCommerceConfiguration {

    @Bean
    public CommerceAdapter myCommerceAdapter() {
        return new CommerceAdapter() {
            @Override
            public CatalogAdapter getCatalogAdapter() {
                return new MyCustomCatalogAdapter();
            }

            @Override
            public CartAdapter getCartAdapter() {
                return new MyCustomCartAdapter();
            }

            @Override
            public CheckoutAdapter getCheckoutAdapter() {
                return new MyCustomCheckoutAdapter();
            }

            @Override
            public OrderAdapter getOrderAdapter() {
                return null; // Not supported -> excluded from /.well-known/ucp
            }

            @Override
            public CustomerAdapter getCustomerAdapter() {
                return null; // Not supported -> excluded from /.well-known/ucp
            }
        };
    }
}
```

---

## Example Storefront & Testing with Any AI Agent

A stateful, agent-ready storefront implementing **all core UCP capabilities + AP2 Mandates** (`catalog.search`, `catalog.lookup`, `cart`, `checkout`, `payment.ap2_mandate`, `order`, `identity_linking`) and **all 3 network transports** (REST `/ucp/v1`, MCP Streamable HTTP `/ucp/mcp`, and A2A `/.well-known/agent-card.json`) is located in [`examples/ucp-example`](examples/ucp-example).

### 1. Start the Sample Storefront
```bash
./gradlew -p examples/ucp-example bootRun
```
The server starts at `http://localhost:8082` and exposes UCP discovery (including AP2 `keys[]`) at `http://localhost:8082/.well-known/ucp`.

### 2. Connect Any MCP Agent (Claude Code, Cursor, Claude Desktop, Gemini CLI, Antigravity)
Point your agent's MCP configuration directly at `http://localhost:8082/ucp/mcp`:

```bash
claude mcp add --transport http ucp-store http://localhost:8082/ucp/mcp
```
Then prompt your agent in plain English:
> *"Search the UCP store for coffee gear, create a cart for `cust-demo-01` with a burr grinder and two ceramic espresso cup sets, sign an AP2 checkout mandate, complete checkout, and give me the confirmed order ID."*

See [`examples/ucp-example/README.md`](examples/ucp-example/README.md) for the full catalog seed list, AP2 configuration toggles, and `curl` walkthroughs.

---

## Changelog

### 0.3.0
* Added configuration-driven **AP2 Mandates** (`dev.ucp.common.payment.ap2_mandate`) conforming to UCP `2026-08-25`:
  * Added `Checkout.Ap2Data` (`merchant_authorization`, `checkout_mandate`), `JwkKey`, `Ap2ErrorCode`, and `Ap2MandateException`.
  * Added RFC 8785 `JcsCanonicalizer` and `Ap2MandateService` supporting `ES256`/`ES384`/`ES512` detached JWS checkout signing and `SD-JWT+kb` (`dc+sd-jwt`) mandate verification and minting.
  * Added `Ap2CommerceAdapter`, `UcpProperties.Ap2` (`ucp.ap2.*`), and top-level `keys[]` + `dev.ucp.common.payment.ap2_mandate` publication in `UcpDiscoveryController`.
* Updated `examples/ucp-example` with configuration-driven AP2 enforcement across REST, MCP (`sign_ap2_checkout_mandate` + `complete_checkout`), and A2A.

### 0.2.0
* Added **`Checkout`** model and **`CheckoutAdapter`** SPI supporting the canonical **`dev.ucp.shopping.checkout`** capability in discovery.
* Added **`ResilientCommerceAdapter`** and `ucp.resilience.*` configuration to automatically decorate `CommerceAdapter` beans with Resilience4j `CircuitBreaker` and `Retry`.
* Made `spring-boot-starter-webmvc` optional (`compileOnly`) and added IDE `spring-configuration-metadata.json` generation.
* Upgraded Gradle wrapper to **9.9.1**.

### 0.1.0
* Upgraded to **Spring Boot 4.1.2** (Jackson 3, Spring Framework 7) and **Resilience4j 2.4.0**; aligned discovery with **UCP Specification 2026-08-25**.

### 0.0.1
* Initial release (Spring Boot 3.2, UCP 2026-01-23).
