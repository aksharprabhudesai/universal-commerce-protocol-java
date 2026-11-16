package dev.ucomprotocol.discovery;

import dev.ucomprotocol.autoconfigure.UcpAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UcpDiscoveryComplianceTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UcpAutoConfiguration.class));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void defaultDiscoveryProfileIncludesRequiredPaymentHandlersAndServicesObjects() throws Exception {
        contextRunner.run(context -> {
            UcpDiscoveryController controller = context.getBean(UcpDiscoveryController.class);
            JsonNode root = jsonMapper.valueToTree(controller.getDiscoveryInfo());

            assertThat(root.path("ucp").path("version").asString()).isEqualTo("2026-08-25");
            assertThat(root.path("ucp").has("services")).isTrue();
            assertThat(root.path("ucp").path("services").isObject()).isTrue();
            assertThat(root.path("ucp").has("payment_handlers")).isTrue();
            assertThat(root.path("ucp").path("payment_handlers").isObject()).isTrue();
            assertThat(root.path("ucp").path("payment_handlers").isEmpty()).isTrue();
            assertThat(root.path("ucp").has("supported_versions")).isFalse();
            assertThat(root.has("keys")).isFalse();
        });
    }

    @Test
    void discoveryBindsSupportedVersionsPaymentHandlersAndDualOkpAndEcKeys() {
        contextRunner
                .withPropertyValues(
                        "ucp.supported-versions[2026-04-08]=https://shop.example.com/.well-known/ucp-2026-04-08.json",
                        "ucp.payment-handlers[com.example.processor_tokenizer][0].id=processor_tokenizer",
                        "ucp.payment-handlers[com.example.processor_tokenizer][0].version=2026-08-25",
                        "ucp.payment-handlers[com.example.processor_tokenizer][0].spec=https://example.com/specs/payments/processor_tokenizer",
                        "ucp.payment-handlers[com.example.processor_tokenizer][0].schema=https://example.com/specs/payments/merchant_tokenizer.json",
                        "ucp.payment-handlers[com.example.processor_tokenizer][0].available-instruments[0].type=card",
                        "ucp.payment-handlers[com.example.processor_tokenizer][0].config.type=CARD",
                        "ucp.keys[0].kid=wba-ed25519-key-1",
                        "ucp.keys[0].kty=OKP",
                        "ucp.keys[0].crv=Ed25519",
                        "ucp.keys[0].x=JrQLj5P_89iXES9-vFgrIy29clF9CC_oPPsw3c5D0bs",
                        "ucp.keys[0].alg=EdDSA",
                        "ucp.ap2.enabled=true",
                        "ucp.ap2.key-id=merchant-es256-key-1")
                .run(context -> {
                    UcpDiscoveryController controller = context.getBean(UcpDiscoveryController.class);
                    JsonNode root = jsonMapper.valueToTree(controller.getDiscoveryInfo());

                    assertThat(root.at("/ucp/supported_versions/2026-04-08").asString())
                            .isEqualTo("https://shop.example.com/.well-known/ucp-2026-04-08.json");

                    JsonNode handler = root.at("/ucp/payment_handlers/com.example.processor_tokenizer/0");
                    assertThat(handler.path("id").asString()).isEqualTo("processor_tokenizer");
                    assertThat(handler.path("version").asString()).isEqualTo("2026-08-25");
                    assertThat(handler.at("/available_instruments/0/type").asString()).isEqualTo("card");
                    assertThat(handler.at("/config/type").asString()).isEqualTo("CARD");

                    JsonNode keys = root.path("keys");
                    assertThat(keys.isArray()).isTrue();
                    assertThat(keys.size()).isEqualTo(2);

                    JsonNode okpKey = keys.get(0);
                    assertThat(okpKey.path("kid").asString()).isEqualTo("wba-ed25519-key-1");
                    assertThat(okpKey.path("kty").asString()).isEqualTo("OKP");
                    assertThat(okpKey.path("crv").asString()).isEqualTo("Ed25519");
                    assertThat(okpKey.path("alg").asString()).isEqualTo("EdDSA");
                    assertThat(okpKey.has("y")).isFalse();

                    JsonNode ecKey = keys.get(1);
                    assertThat(ecKey.path("kid").asString()).isEqualTo("merchant-es256-key-1");
                    assertThat(ecKey.path("kty").asString()).isEqualTo("EC");
                    assertThat(ecKey.path("crv").asString()).isEqualTo("P-256");
                    assertThat(ecKey.has("y")).isTrue();
                });
    }

    @Test
    void multiParentCapabilitySerializesExtendsAsArray() {
        UcpDiscoveryController.Capability discountExt = new UcpDiscoveryController.Capability(
                "2026-08-25",
                "https://ucp.dev/2026-08-25/specification/shopping/extensions/discount/",
                "https://ucp.dev/2026-08-25/schemas/shopping/discount.json",
                List.of("dev.ucp.shopping.checkout", "dev.ucp.shopping.cart"),
                Map.of());

        JsonNode node = jsonMapper.valueToTree(discountExt);
        assertThat(node.path("extends").isArray()).isTrue();
        assertThat(node.path("extends").size()).isEqualTo(2);
        assertThat(node.at("/extends/0").asString()).isEqualTo("dev.ucp.shopping.checkout");
        assertThat(node.at("/extends/1").asString()).isEqualTo("dev.ucp.shopping.cart");
    }
}
