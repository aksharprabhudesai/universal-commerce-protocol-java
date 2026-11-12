# UCP Java Sample Storefront Application

A stateful, agent-ready Spring Boot storefront built on `ucp-spring-boot-starter:0.3.0` (`UCP 2026-08-25`). It implements **all core UCP capabilities plus configuration-driven AP2 Mandates** and exposes **all three UCP network transports** so you can test end-to-end cryptographic agentic commerce locally with any AI agent or HTTP client.

---

## Capabilities & Transports Included

### Capabilities (`/.well-known/ucp`)
| Capability | Source | What It Does |
|---|---|---|
| `dev.ucp.shopping.catalog.search` | `CatalogAdapter` | Keyword & category search across 6 seeded products |
| `dev.ucp.shopping.catalog.lookup` | `CatalogAdapter` | Lookup by product ID or SKU |
| `dev.ucp.shopping.cart` | `CartAdapter` | Stateful cart creation, line-item add/update/remove, live totals |
| `dev.ucp.shopping.checkout` | `CheckoutAdapter` | UCP checkout lifecycle (`READY_FOR_COMPLETE` -> `COMPLETED` / `CANCELED`) |
| `dev.ucp.common.payment.ap2_mandate` | `ucp.ap2.enabled=true` | Signs checkouts with detached JWS (`ap2.merchant_authorization`) and verifies `SD-JWT+kb` (`ap2.checkout_mandate`) |
| `dev.ucp.shopping.order` | `OrderAdapter` | Confirmed order creation, customer order history, order cancellation |
| `dev.ucp.common.identity_linking` | `CustomerAdapter` | Customer lookup by ID/email, creation, and profile updates |

All adapter calls are automatically wrapped in Resilience4j `CircuitBreaker` + `Retry` (`ResilientCommerceAdapter`) and AP2 mandate verification (`Ap2CommerceAdapter`).

### Configuration-Driven AP2 Toggle (`src/main/resources/application.properties`)
```properties
ucp.ap2.enabled=true
ucp.ap2.key-id=storefront-ap2-2026
ucp.ap2.algorithm=ES256
ucp.ap2.require-checkout-mandate=true
ucp.ap2.allow-embedded-agent-jwk=true
```
Set `ucp.ap2.enabled=false` (or `--ucp.ap2.enabled=false` on the command line) to switch back to standard non-mandate checkout without changing a single line of code.

### Transports Advertised
| Transport | Endpoint | Purpose |
|---|---|---|
| **UCP Discovery** | `GET http://localhost:8082/.well-known/ucp` | Canonical UCP `2026-08-25` profile + merchant EC public JWK (`keys[]`) |
| **MCP (Streamable HTTP)** | `POST http://localhost:8082/ucp/mcp` | JSON-RPC 2.0 MCP server exposing 17 commerce & AP2 tools |
| **REST** | `http://localhost:8082/ucp/v1/*` | RESTful shopping, AP2 demo wallet signing, & identity endpoints |
| **A2A Agent Card** | `GET http://localhost:8082/.well-known/agent-card.json` | Agent-to-Agent discovery metadata & 6 skills |

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

Run the automated end-to-end test suite (covers Discovery + `keys[]`, A2A, REST AP2 mandate flow, MCP JSON-RPC AP2 flow, and `ucp.ap2.enabled=false` toggle):

```bash
./gradlew -p examples/ucp-example test
```

---

## Testing with Any AI Agent

### Option 1: MCP-Compatible Agents (Claude Code, Cursor, Claude Desktop, Gemini CLI, Antigravity)

```bash
claude mcp add --transport http ucp-store http://localhost:8082/ucp/mcp
```

**Try these natural-language prompts in your AI agent:**
1. *"Search the UCP store for coffee gear under \$200 and show me what's in stock."*
2. *"Create a cart for customer `cust-demo-01`, add 1 conical burr grinder and 2 ceramic espresso cup sets, and tell me the cart total."*
3. *"Start a checkout session from that cart, sign an AP2 checkout mandate for it, complete the checkout, and confirm the final order ID and status."*
4. *"Look up all orders placed by `cust-demo-01`, then cancel the order we just placed."*

---

## Manual `curl` Walkthrough (with AP2 Mandates)

### 1. Inspect UCP Discovery & Merchant Public Key (`/.well-known/ucp`)
```bash
curl -s http://localhost:8082/.well-known/ucp | python3 -m json.tool
```

### 2. End-to-End AP2 Handshake via REST (`/ucp/v1`)
```bash
# 1. Create cart with items
curl -s -X POST http://localhost:8082/ucp/v1/carts \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-demo-01","items":[{"productId":"prod-grinder-02","quantity":1},{"productId":"prod-mugs-03","quantity":2}]}'

# 2. Create checkout session -> returns ap2.merchant_authorization (replace cart-1001 if needed)
curl -s -X POST http://localhost:8082/ucp/v1/checkout-sessions \
  -H "Content-Type: application/json" \
  -d '{"cartId":"cart-1001"}'

# 3. Attempting complete without an AP2 mandate returns HTTP 400 {"error":"mandate_required"}
curl -s -X POST http://localhost:8082/ucp/v1/checkout-sessions/chk-2001/complete

# 4. Sign an SD-JWT+kb checkout mandate via the demo wallet helper
MANDATE=$(curl -s -X POST http://localhost:8082/ucp/v1/ap2/mandates/sign \
  -H "Content-Type: application/json" \
  -d '{"checkoutId":"chk-2001"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['checkoutMandate'])")

# 5. Complete checkout with ap2.checkout_mandate -> places confirmed order
curl -s -X POST http://localhost:8082/ucp/v1/checkout-sessions/chk-2001/complete \
  -H "Content-Type: application/json" \
  -d "{\"ap2\":{\"checkout_mandate\":\"$MANDATE\"}}"
```

### 3. End-to-End via MCP JSON-RPC (`/ucp/mcp`)
```bash
# List all 17 UCP + AP2 MCP tools
curl -s -X POST http://localhost:8082/ucp/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | python3 -m json.tool
```
