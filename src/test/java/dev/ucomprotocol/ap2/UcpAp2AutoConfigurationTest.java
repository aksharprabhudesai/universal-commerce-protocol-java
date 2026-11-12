package dev.ucomprotocol.ap2;

import dev.ucomprotocol.autoconfigure.UcpAutoConfiguration;
import dev.ucomprotocol.discovery.UcpDiscoveryController;
import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;
import dev.ucomprotocol.spi.CommerceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UcpAp2AutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UcpAutoConfiguration.class));

    @Test
    void ap2ShouldBeDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(Ap2MandateService.class);
            CommerceAdapter adapter = context.getBean(CommerceAdapter.class);
            assertThat(adapter).isNotInstanceOf(Ap2CommerceAdapter.class);

            UcpDiscoveryController discovery = context.getBean(UcpDiscoveryController.class);
            UcpDiscoveryController.DiscoveryResponse info = discovery.getDiscoveryInfo();
            assertThat(info.keys()).isNull();
            assertThat(info.ucp().capabilities()).doesNotContainKey(UcpDiscoveryController.AP2_MANDATE_CAPABILITY);

            Cart cart = adapter.getCartAdapter().createCart("cust-1");
            Checkout created = adapter.getCheckoutAdapter().createCheckout(cart);
            assertThat(created.getAp2()).isNull();
            Checkout completed = adapter.getCheckoutAdapter().completeCheckout(created.getId());
            assertThat(completed.getStatus()).isEqualTo(Checkout.CheckoutStatus.COMPLETED);
        });
    }

    @Test
    void ap2EnabledShouldAdvertiseCapabilitySignCheckoutsAndEnforceMandates() {
        contextRunner
                .withPropertyValues(
                        "ucp.ap2.enabled=true",
                        "ucp.ap2.key-id=test-merchant-key",
                        "ucp.ap2.allow-embedded-agent-jwk=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(Ap2MandateService.class);
                    CommerceAdapter adapter = context.getBean(CommerceAdapter.class);
                    assertThat(adapter).isInstanceOf(Ap2CommerceAdapter.class);

                    UcpDiscoveryController discovery = context.getBean(UcpDiscoveryController.class);
                    UcpDiscoveryController.DiscoveryResponse info = discovery.getDiscoveryInfo();
                    assertThat(info.keys()).hasSize(1);
                    assertThat(info.keys().get(0).kid()).isEqualTo("test-merchant-key");
                    assertThat(info.keys().get(0).crv()).isEqualTo("P-256");
                    assertThat(info.ucp().capabilities())
                            .containsKey(UcpDiscoveryController.AP2_MANDATE_CAPABILITY);
                    assertThat(info.ucp().capabilities().get(UcpDiscoveryController.AP2_MANDATE_CAPABILITY).get(0)
                            .parentCapability()).isEqualTo("dev.ucp.shopping.checkout");

                    Cart cart = Cart.builder()
                            .id("cart-1")
                            .customerId("cust-1")
                            .currency("USD")
                            .items(List.of(Cart.CartItem.builder()
                                    .id("item-1")
                                    .productId("prod-1")
                                    .name("Pour-Over Kettle")
                                    .quantity(1)
                                    .unitPrice(new BigDecimal("85.00"))
                                    .totalPrice(new BigDecimal("85.00"))
                                    .build()))
                            .totalAmount(new BigDecimal("85.00"))
                            .build();

                    Checkout created = adapter.getCheckoutAdapter().createCheckout(cart);
                    assertThat(created.getAp2()).isNotNull();
                    assertThat(created.getAp2().getMerchantAuthorization()).contains("..");

                    // Completing without mandate must fail with MANDATE_REQUIRED
                    assertThatThrownBy(() -> adapter.getCheckoutAdapter().completeCheckout(created.getId()))
                            .isInstanceOf(Ap2MandateException.class)
                            .extracting(ex -> ((Ap2MandateException) ex).getErrorCode())
                            .isEqualTo(Ap2ErrorCode.MANDATE_REQUIRED);

                    // Minting a valid SD-JWT+kb mandate succeeds
                    Ap2MandateService ap2Service = context.getBean(Ap2MandateService.class);
                    KeyPair agentKey = Ap2MandateService.generateEcKeyPair("ES256");
                    String mandate = ap2Service.mintCheckoutMandate(
                            created,
                            (ECPrivateKey) agentKey.getPrivate(),
                            (ECPublicKey) agentKey.getPublic(),
                            "sandbox-agent-1",
                            Duration.ofMinutes(5));

                    Checkout completed = adapter.getCheckoutAdapter().completeCheckout(
                            created.getId(),
                            Checkout.Ap2Data.builder().checkoutMandate(mandate).build());
                    assertThat(completed.getStatus()).isEqualTo(Checkout.CheckoutStatus.COMPLETED);
                    assertThat(completed.getAp2().getCheckoutMandate()).isEqualTo(mandate);
                    assertThat(completed.getAp2().getMerchantAuthorization()).contains("..");
                });
    }

    @Test
    void ap2PermissiveModeShouldSignCheckoutsWithoutRequiringMandate() {
        contextRunner
                .withPropertyValues(
                        "ucp.ap2.enabled=true",
                        "ucp.ap2.require-checkout-mandate=false")
                .run(context -> {
                    CommerceAdapter adapter = context.getBean(CommerceAdapter.class);
                    Cart cart = adapter.getCartAdapter().createCart("cust-2");
                    Checkout created = adapter.getCheckoutAdapter().createCheckout(cart);
                    assertThat(created.getAp2()).isNotNull();
                    assertThat(created.getAp2().getMerchantAuthorization()).isNotBlank();

                    Checkout completed = adapter.getCheckoutAdapter().completeCheckout(created.getId());
                    assertThat(completed.getStatus()).isEqualTo(Checkout.CheckoutStatus.COMPLETED);
                    assertThat(completed.getAp2().getMerchantAuthorization()).isNotBlank();
                });
    }

    @Test
    void ap2ShouldBindExplicitKeysAndTrustedPlatformKeysFromProperties() {
        KeyPair merchantKp = Ap2MandateService.generateEcKeyPair("ES256");
        KeyPair platformKp = Ap2MandateService.generateEcKeyPair("ES256");
        String privB64 = Base64.getEncoder().encodeToString(merchantKp.getPrivate().getEncoded());
        String pubB64 = Base64.getEncoder().encodeToString(merchantKp.getPublic().getEncoded());
        JwkKey platformJwk = Ap2MandateService.toPublicJwk(
                (ECPublicKey) platformKp.getPublic(), "platform-prod-key-1", "ES256");

        contextRunner
                .withPropertyValues(
                        "ucp.ap2.enabled=true",
                        "ucp.ap2.key-id=prod-merchant-1",
                        "ucp.ap2.auto-generate-ephemeral-key=false",
                        "ucp.ap2.signing-private-key=" + privB64,
                        "ucp.ap2.signing-public-key=" + pubB64,
                        "ucp.ap2.allow-embedded-agent-jwk=false",
                        "ucp.ap2.trusted-platform-keys[0].kid=" + platformJwk.kid(),
                        "ucp.ap2.trusted-platform-keys[0].kty=EC",
                        "ucp.ap2.trusted-platform-keys[0].crv=P-256",
                        "ucp.ap2.trusted-platform-keys[0].x=" + platformJwk.x(),
                        "ucp.ap2.trusted-platform-keys[0].y=" + platformJwk.y(),
                        "ucp.ap2.trusted-platform-keys[0].alg=ES256")
                .run(context -> {
                    CommerceAdapter adapter = context.getBean(CommerceAdapter.class);
                    Ap2MandateService ap2Service = context.getBean(Ap2MandateService.class);

                    Cart cart = adapter.getCartAdapter().createCart("cust-prod");
                    Checkout created = adapter.getCheckoutAdapter().createCheckout(cart);
                    String mandate = ap2Service.mintCheckoutMandate(
                            created,
                            (ECPrivateKey) platformKp.getPrivate(),
                            (ECPublicKey) platformKp.getPublic(),
                            "platform-prod-key-1",
                            Duration.ofMinutes(5));

                    Checkout completed = adapter.getCheckoutAdapter().completeCheckout(
                            created.getId(),
                            Checkout.Ap2Data.builder().checkoutMandate(mandate).build());
                    assertThat(completed.getStatus()).isEqualTo(Checkout.CheckoutStatus.COMPLETED);
                });
    }
}
