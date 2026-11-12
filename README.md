# UCP Java - Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/dev.ucomprotocol/ucp-spring-boot-starter.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/dev.ucomprotocol/ucp-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

The **Universal Commerce Protocol (UCP)** library is a standardized, protocol-agnostic framework designed for Spring Boot. It abstracts the complexities of integrating with multiple commerce platforms (Shopify, Magento, Commercetools, etc.) into a single, unified interface.

* **Website**: [ucomprotocol.dev](https://ucomprotocol.dev)
* **Official Specification**: [ucp.dev](https://ucp.dev) (Version `2026-08-25`)
* **Javadoc Reference**: [ucomprotocol.dev/javadocs](https://ucomprotocol.dev/javadocs/)

---

## Features

* **Unified Domain Models:** Standardized POJOs for `Product`, `Cart`, `Checkout`, `Order`, and `Customer`.
* **Provider Agnostic:** Switch commerce providers via configuration without touching business logic.
* **Spring Boot Auto-Configuration:** Automatically registers the active `CommerceAdapter` and IDE configuration metadata (`spring-configuration-metadata.json`).
* **Discovery Endpoint:** Exposes protocol metadata at `/.well-known/ucp` conforming to UCP specification `2026-08-25` when Spring Web MVC is on the classpath.
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
    implementation 'dev.ucomprotocol:ucp-spring-boot-starter:0.2.0'
}
```

#### Gradle (Kotlin DSL)
```kotlin
dependencies {
    implementation("dev.ucomprotocol:ucp-spring-boot-starter:0.2.0")
}
```

#### Maven
```xml
<dependency>
    <groupId>dev.ucomprotocol</groupId>
    <artifactId>ucp-spring-boot-starter</artifactId>
    <version>0.2.0</version>
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

## Discovery Endpoint Configuration

UCP Java provides an **Automatic Discovery Endpoint** at `/.well-known/ucp`. This allows clients, agents, and external systems to discover which capabilities and transport services your application supports.

### How to Enable

Include `spring-boot-starter-webmvc` alongside UCP Java. The starter detects Servlet Web MVC and automatically registers the controller (non-web and WebFlux apps do not pull in Tomcat).

```groovy
dependencies {
    implementation 'dev.ucomprotocol:ucp-spring-boot-starter:0.2.0'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
}
```

### Advertising Transport Services (Optional)

Configure transport endpoints for the `dev.ucp.shopping` service in `application.properties`:

```properties
# REST transport endpoint
ucp.services.rest-endpoint=https://shop.example.com/ucp/v1

# MCP (Model Context Protocol) transport endpoint
ucp.services.mcp-endpoint=https://shop.example.com/ucp/mcp

# A2A (Agent-to-Agent) card URL
ucp.services.a2a-endpoint=https://shop.example.com/.well-known/agent-card.json

# Embedded OpenRPC transport
ucp.services.embedded-enabled=true
```

### Verification

Once your application starts, query the endpoint:

```bash
curl http://localhost:8080/.well-known/ucp
```

**Response Example (conforming to UCP 2026-08-25):**
```json
{
  "ucp": {
    "version": "2026-08-25",
    "services": {
      "dev.ucp.shopping": [
        {
          "version": "2026-08-25",
          "spec": "https://ucp.dev/2026-08-25/specification/overview/",
          "transport": "rest",
          "endpoint": "https://shop.example.com/ucp/v1",
          "schema": "https://ucp.dev/2026-08-25/services/shopping/rest.openapi.json"
        }
      ]
    },
    "capabilities": {
      "dev.ucp.shopping.catalog.search": [
        {
          "version": "2026-08-25",
          "spec": "https://ucp.dev/2026-08-25/specification/shopping/catalog/search/",
          "schema": "https://ucp.dev/2026-08-25/schemas/shopping/catalog_search.json"
        }
      ],
      "dev.ucp.shopping.catalog.lookup": [
        {
          "version": "2026-08-25",
          "spec": "https://ucp.dev/2026-08-25/specification/shopping/catalog/lookup/",
          "schema": "https://ucp.dev/2026-08-25/schemas/shopping/catalog_lookup.json"
        }
      ],
      "dev.ucp.shopping.cart": [
        {
          "version": "2026-08-25",
          "spec": "https://ucp.dev/2026-08-25/specification/shopping/cart/",
          "schema": "https://ucp.dev/2026-08-25/schemas/shopping/cart.json"
        }
      ],
      "dev.ucp.shopping.checkout": [
        {
          "version": "2026-08-25",
          "spec": "https://ucp.dev/2026-08-25/specification/shopping/checkout/",
          "schema": "https://ucp.dev/2026-08-25/schemas/shopping/checkout.json"
        }
      ],
      "dev.ucp.shopping.order": [
        {
          "version": "2026-08-25",
          "spec": "https://ucp.dev/2026-08-25/specification/shopping/order/",
          "schema": "https://ucp.dev/2026-08-25/schemas/shopping/order.json"
        }
      ],
      "dev.ucp.common.identity_linking": [
        {
          "version": "2026-08-25",
          "spec": "https://ucp.dev/2026-08-25/specification/common/identity-linking/",
          "schema": "https://ucp.dev/2026-08-25/schemas/common/identity_linking.json"
        }
      ]
    }
  }
}
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

---

## Custom Adapters

To integrate a custom commerce provider, implement the `CommerceAdapter` interface and declare it as a Spring `@Bean`. The auto-configuration automatically backs off from the mock adapter and wraps your adapter with Resilience4j protection:

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

## Example Project

A complete working example demonstrating configuration, the mock adapter, and discovery endpoint verification is located in [`examples/ucp-example`](examples/ucp-example).

```bash
cd examples/ucp-example
../../gradlew bootRun
```

Query the running example:
```bash
curl http://localhost:8082/.well-known/ucp
```

---

## Changelog

### 0.2.0
* Added **`Checkout`** model and **`CheckoutAdapter`** SPI supporting the canonical **`dev.ucp.shopping.checkout`** capability in discovery (via backwards-compatible `default` method on `CommerceAdapter`).
* Added **`ResilientCommerceAdapter`** and `ucp.resilience.*` configuration to automatically decorate `CommerceAdapter` beans with Resilience4j `CircuitBreaker` and `Retry`.
* Made `spring-boot-starter-webmvc` optional (`compileOnly`) and guarded web auto-configuration with `@ConditionalOnClass` and `@ConditionalOnBean(CommerceAdapter.class)` so non-web and WebFlux applications are not forced onto Servlet/Tomcat.
* Added `spring-boot-configuration-processor` to generate `META-INF/spring-configuration-metadata.json` for IDE property autocomplete.
* Added `embedded` transport support (`ucp.services.embedded-enabled`) and `extends`/`config` fields on discovery `Capability`.
* Upgraded Gradle wrapper to **9.9.1** and aligned API reference documentation with SPI signatures.

### 0.1.0
* Upgraded to **Spring Boot 4.1.2** (Jackson 3, Spring Framework 7) and **Resilience4j 2.4.0**.
* Discovery endpoint (`/.well-known/ucp`) aligned with **UCP Specification 2026-08-25** (`capabilities` and `services` maps, schema URLs, `catalog.search` / `catalog.lookup`, `identity_linking`, and `ucp.services.*`).

### 0.0.1
* Initial release (Spring Boot 3.2, UCP 2026-01-23).
