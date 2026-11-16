package dev.ucomprotocol.ap2;

import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ap2MandateServiceTest {

    private KeyPair merchantKeys;
    private KeyPair agentKeys;
    private Ap2MandateService ap2Service;

    @BeforeEach
    void setUp() {
        merchantKeys = Ap2MandateService.generateEcKeyPair("ES256");
        agentKeys = Ap2MandateService.generateEcKeyPair("ES256");
        JwkKey trustedAgentJwk = Ap2MandateService.toPublicJwk(
                (ECPublicKey) agentKeys.getPublic(), "agent-key-1", "ES256");

        ap2Service = new Ap2MandateService(
                "merchant-key-2026",
                "ES256",
                (ECPrivateKey) merchantKeys.getPrivate(),
                (ECPublicKey) merchantKeys.getPublic(),
                true,
                true,
                false,
                List.of(trustedAgentJwk));
    }

    @Test
    void jcsCanonicalizerSortsKeysAndNormalizesDecimals() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        var tree = mapper.readTree("{\"z\":217.00,\"a\":{\"b\":34.50,\"a\":true}}");
        String canonical = new String(JcsCanonicalizer.canonicalize(tree), StandardCharsets.UTF_8);
        assertEquals("{\"a\":{\"a\":true,\"b\":34.5},\"z\":217}", canonical);
    }

    @Test
    void signsCheckoutAndVerifiesValidSdJwtMandate() {
        Checkout checkout = sampleCheckout();
        ap2Service.signCheckout(checkout);

        assertNotNull(checkout.getAp2());
        String merchantAuth = checkout.getAp2().getMerchantAuthorization();
        assertNotNull(merchantAuth);
        assertTrue(merchantAuth.matches("^[A-Za-z0-9_-]+\\.\\.[A-Za-z0-9_-]+$"));

        String mandate = ap2Service.mintCheckoutMandate(
                checkout,
                (ECPrivateKey) agentKeys.getPrivate(),
                (ECPublicKey) agentKeys.getPublic(),
                "agent-key-1",
                Duration.ofMinutes(5));

        Checkout.Ap2Data submitted = Checkout.Ap2Data.builder()
                .checkoutMandate(mandate)
                .build();

        // Should verify cleanly without throwing
        ap2Service.verifyCheckoutMandate(checkout, submitted);
    }

    @Test
    void rejectsMissingMandateWhenRequired() {
        Checkout checkout = ap2Service.signCheckout(sampleCheckout());
        Ap2MandateException ex = assertThrows(Ap2MandateException.class,
                () -> ap2Service.verifyCheckoutMandate(checkout, null));
        assertEquals(Ap2ErrorCode.MANDATE_REQUIRED, ex.getErrorCode());
    }

    @Test
    void rejectsExpiredMandate() {
        Checkout checkout = ap2Service.signCheckout(sampleCheckout());
        String expiredMandate = ap2Service.mintCheckoutMandate(
                checkout,
                (ECPrivateKey) agentKeys.getPrivate(),
                (ECPublicKey) agentKeys.getPublic(),
                "agent-key-1",
                Duration.ofSeconds(-30));

        Ap2MandateException ex = assertThrows(Ap2MandateException.class,
                () -> ap2Service.verifyCheckoutMandate(checkout,
                        Checkout.Ap2Data.builder().checkoutMandate(expiredMandate).build()));
        assertEquals(Ap2ErrorCode.MANDATE_EXPIRED, ex.getErrorCode());
    }

    @Test
    void rejectsUntrustedAgentKeyWhenEmbeddedJwkDisallowed() {
        Checkout checkout = ap2Service.signCheckout(sampleCheckout());
        KeyPair unknownAgent = Ap2MandateService.generateEcKeyPair("ES256");
        String mandate = ap2Service.mintCheckoutMandate(
                checkout,
                (ECPrivateKey) unknownAgent.getPrivate(),
                (ECPublicKey) unknownAgent.getPublic(),
                "untrusted-agent-99",
                Duration.ofMinutes(5));

        Ap2MandateException ex = assertThrows(Ap2MandateException.class,
                () -> ap2Service.verifyCheckoutMandate(checkout,
                        Checkout.Ap2Data.builder().checkoutMandate(mandate).build()));
        assertEquals(Ap2ErrorCode.AGENT_MISSING_KEY, ex.getErrorCode());
    }

    @Test
    void rejectsScopeMismatchWhenTotalChangesAfterMandateSigned() {
        Checkout checkout = ap2Service.signCheckout(sampleCheckout());
        String mandate = ap2Service.mintCheckoutMandate(
                checkout,
                (ECPrivateKey) agentKeys.getPrivate(),
                (ECPublicKey) agentKeys.getPublic(),
                "agent-key-1",
                Duration.ofMinutes(5));

        // Mutate session total after user signed the mandate
        checkout.setTotalAmount(new BigDecimal("999.00"));

        Ap2MandateException ex = assertThrows(Ap2MandateException.class,
                () -> ap2Service.verifyCheckoutMandate(checkout,
                        Checkout.Ap2Data.builder().checkoutMandate(mandate).build()));
        assertEquals(Ap2ErrorCode.MANDATE_SCOPE_MISMATCH, ex.getErrorCode());
    }

    @Test
    void supportsEd25519OkpJwkSerializationAndMandateVerification() throws Exception {
        KeyPair edAgent = Ap2MandateService.generateEd25519KeyPair();
        JwkKey okpJwk = Ap2MandateService.toPublicJwk(edAgent.getPublic(), "wba-agent-ed25519", "EdDSA");

        assertEquals("OKP", okpJwk.kty());
        assertEquals("Ed25519", okpJwk.crv());
        assertEquals("EdDSA", okpJwk.alg());
        assertNull(okpJwk.y());

        // Verify RFC 8037 raw 32-byte little-endian encoding matches X.509 SubjectPublicKeyInfo tail
        byte[] x509 = edAgent.getPublic().getEncoded();
        byte[] expectedRaw32 = Arrays.copyOfRange(x509, x509.length - 32, x509.length);
        assertArrayEquals(expectedRaw32, Base64.getUrlDecoder().decode(okpJwk.x()));

        // Verify JSON serialization omits 'y'
        JsonMapper mapper = JsonMapper.builder().build();
        String json = mapper.writeValueAsString(okpJwk);
        assertFalse(json.contains("\"y\""));

        // Register OKP key + unknown open-vocabulary key and verify EdDSA mandate end-to-end
        JwkKey unknownFutureKey = new JwkKey("future-key-1", "AKP", "ML-DSA-65", "AAAA", null, "sig", "ML-DSA-65");
        Ap2MandateService serviceWithOkp = new Ap2MandateService(
                "merchant-key-2026",
                "ES256",
                merchantKeys.getPrivate(),
                merchantKeys.getPublic(),
                true,
                true,
                false,
                List.of(okpJwk, unknownFutureKey));

        Checkout checkout = serviceWithOkp.signCheckout(sampleCheckout());
        String edMandate = serviceWithOkp.mintCheckoutMandate(
                checkout,
                edAgent.getPrivate(),
                edAgent.getPublic(),
                "wba-agent-ed25519",
                Duration.ofMinutes(5));

        serviceWithOkp.verifyCheckoutMandate(
                checkout,
                Checkout.Ap2Data.builder().checkoutMandate(edMandate).build());
    }

    private Checkout sampleCheckout() {
        return Checkout.builder()
                .id("chk-101")
                .customerId("cust-demo-01")
                .currency("USD")
                .status(Checkout.CheckoutStatus.READY_FOR_COMPLETE)
                .items(List.of(Cart.CartItem.builder()
                        .id("item-1")
                        .productId("prod-grinder-02")
                        .name("Conical Burr Coffee Grinder")
                        .sku("GRD-BURR-02")
                        .quantity(1)
                        .unitPrice(new BigDecimal("149.00"))
                        .totalPrice(new BigDecimal("149.00"))
                        .build()))
                .totalAmount(new BigDecimal("149.00"))
                .expiresAt(Instant.parse("2026-12-01T00:00:00Z"))
                .build();
    }
}
