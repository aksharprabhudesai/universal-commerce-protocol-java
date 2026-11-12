# UCP Java Sample Storefront Application

A stateful, agent-ready Spring Boot storefront built on `ucp-spring-boot-starter:0.2.0` (`UCP 2026-08-25`). It implements **all core UCP capabilities** and exposes **all three UCP network transports** so you can test end-to-end agentic commerce locally with any AI agent or HTTP client.

---

## Capabilities & Transports Included

### Capabilities (`/.well-known/ucp`)
| Capability | Adapter | What It Does |
|---|---|---|
| `dev.ucp.shopping.catalog.search` | `CatalogAdapter` | Keyword & category search across 6 seeded products |
| `dev.ucp.shopping.catalog.lookup` | `CatalogAdapter` | Lookup by product ID or SKU |
| `dev.ucp.shopping.cart` | `CartAdapter` | Stateful cart creation, line-item add/update/remove, live totals |
| `dev.ucp.shopping.checkout` | `CheckoutAdapter` | UCP checkout lifecycle (`READY_FOR_COMPLETE` -> `COMPLETED` / `CANCELED`) |
| `dev.ucp.shopping.order` | `OrderAdapter` | Confirmed order creation, customer order history, order cancellation |
| `dev.ucp.common.identity_linking` | `CustomerAdapter` | Customer lookup by ID/email, creation, and profile updates |

All adapter calls are automatically wrapped in Resilience4j `CircuitBreaker` + `Retry` via `ResilientCommerceAdapter`.

### Transports Advertised
| Transport | Endpoint | Purpose |
|---|---|---|
| **UCP Discovery** | `GET http://localhost:8082/.well-known/ucp` | Canonical UCP `2026-08-25` business profile |
| **MCP (Streamable HTTP)** | `POST http://localhost:8082/ucp/mcp` | JSON-RPC 2.0 MCP server exposing 16 commerce tools |
| **REST** | `http://localhost:8082/ucp/v1/*` | RESTful shopping & identity endpoints |
| **A2A Agent Card** | `GET http://localhost:8082/.well-known/agent-card.json` | Agent-to-Agent discovery metadata & skills |

---

## Seeded Store Data

* **Demo Customer**: `cust-demo-01` (`Alex Rivera`, `alex@example.com`)
* **Products**:
  * `prod-espresso-01` (`ESP-PRO-01`) — Barista Pro Espresso Machine (\$499.00, `coffee`)
  * `prod-grinder-02` (`GRD-BURR-02`) — Conical Burr Coffee Grinder (\$149.00, `coffee`)
  * `prod-mugs-03` (`MUG-DBL-03`) — Double-Wall Ceramic Espresso Cups (\$34.00, `accessories`)
  * `prod-kettle-04` (`KTL-GNK-04`) — Precision Gooseneck Pour-Over Kettle (\$89.00, `coffee`)
  * `prod-scale-05` (`SCL-DIG-05`) — Smart Digital Coffee Scale (\$59.00, `electronics`)
  * `prod-keyboard-06` (`KBD-MECH-06`) — Wireless Mechanical Developer Keyboard (\$179.00, `electronics`)

---

## Quick Start

From the repository root:

```bash
./gradlew -p examples/ucp-example bootRun
```

The sample storefront starts on **`http://localhost:8082`**.

Run the automated end-to-end test suite (covers Discovery, A2A, REST shopping flow, and MCP JSON-RPC agent flow):

```bash
./gradlew -p examples/ucp-example test
```

---

## Testing with Any AI Agent

### Option 1: MCP-Compatible Agents (Claude Code, Cursor, Claude Desktop, Gemini CLI, Antigravity)

Because the app exposes a native **MCP Streamable HTTP** endpoint at `http://localhost:8082/ucp/mcp`, any MCP client can connect directly without extra wrappers.

**Claude Code CLI:**
```bash
claude mcp add --transport http ucp-store http://localhost:8082/ucp/mcp
```

**Generic `mcpServers` JSON (`settings.json` / `mcp_config.json` / `.cursor/mcp.json`):**
```json
{
  "mcpServers": {
    "ucp-store": {
      "url": "http://localhost:8082/ucp/mcp"
    }
  }
}
```

**Try these natural-language prompts in your AI agent:**
1. *"Search the UCP store for coffee gear under \$200 and show me what's in stock."*
2. *"Create a cart for customer `cust-demo-01`, add 1 conical burr grinder and 2 ceramic espresso cup sets, and tell me the cart total."*
3. *"Start a checkout session from that cart, complete the checkout, and confirm the final order ID and status."*
4. *"Look up all orders placed by `cust-demo-01`, then cancel the order we just placed."*

---

### Option 2: Any Autonomous / HTTP-Capable AI Agent (OpenAI, Gemini, LangChain, CrewAI, Web Agents)

Agents with HTTP/fetch tools can self-discover the storefront directly from `/.well-known/ucp` with zero pre-configuration. Give the agent this prompt:

> *"Connect to the UCP merchant at `http://localhost:8082/.well-known/ucp`. Discover its supported capabilities and transport endpoints (`mcp`, `rest`, or `a2a`), find a mechanical keyboard and a digital scale, place an order for customer `cust-demo-01`, and report the confirmed order ID and total."*

---

### Option 3: A2A (Agent-to-Agent) Discovery

Point any A2A orchestrator at the merchant domain (`http://localhost:8082`) or directly fetch:

```bash
curl http://localhost:8082/.well-known/agent-card.json
```

---

## Manual `curl` Walkthrough

### 1. Inspect UCP Discovery (`/.well-known/ucp`)
```bash
curl -s http://localhost:8082/.well-known/ucp | python3 -m json.tool
```

### 2. End-to-End via REST (`/ucp/v1`)
```bash
# Search catalog
curl -s -X POST http://localhost:8082/ucp/v1/catalog/search \
  -H "Content-Type: application/json" \
  -d '{"query":"espresso"}'

# Create cart with items
curl -s -X POST http://localhost:8082/ucp/v1/carts \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-demo-01","items":[{"productId":"prod-grinder-02","quantity":1},{"productId":"prod-mugs-03","quantity":2}]}'

# Create checkout session (replace cart-1001 with returned cart ID)
curl -s -X POST http://localhost:8082/ucp/v1/checkout-sessions \
  -H "Content-Type: application/json" \
  -d '{"cartId":"cart-1001"}'

# Complete checkout -> places confirmed order (replace chk-2001 with returned checkout ID)
curl -s -X POST http://localhost:8082/ucp/v1/checkout-sessions/chk-2001/complete

# Fetch placed order (replace ord-3001 with returned orderId)
curl -s http://localhost:8082/ucp/v1/orders/ord-3001
```

### 3. End-to-End via MCP JSON-RPC (`/ucp/mcp`)
```bash
# List all 16 UCP MCP tools
curl -s -X POST http://localhost:8082/ucp/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | python3 -m json.tool

# Invoke search_catalog tool via MCP
curl -s -X POST http://localhost:8082/ucp/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_catalog","arguments":{"category":"coffee"}}}' | python3 -m json.tool
```
