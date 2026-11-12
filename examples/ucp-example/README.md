# UCP Example Application

This is an example Spring Boot application demonstrating how to use the `universal-commerce-protocol-java` library.

## Project Structure

This project is configured as a composite build, including the root `universal-commerce-protocol-java` build. This allows it to use the local version of the library without needing to publish artifacts to a repository.

## Prerequisites

- Java 17 or higher
- Git

## How to Run

1. Navigate to the example directory:
   ```bash
   cd examples/ucp-example
   ```

2. Run the application using Gradle:
   ```bash
   ../../gradlew bootRun
   ```

   The application will start on port **8082**.

## Verify the Application

Once the application is running, you can access the UCP Discovery endpoint:

```bash
curl http://localhost:8082/.well-known/ucp
```

Expected output (JSON):
```json
{
  "ucp": {
    "version": "2026-08-25",
    "services": {},
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

The capabilities listed depend on what the Mock Adapter is configured to support (by default, it supports all core capabilities).

## Configuration

The application is configured in `src/main/resources/application.properties`:

```properties
server.port=8082
ucp.provider=mock
```

You can change `ucp.provider` to switch implementations (e.g., `shopify` if available).
