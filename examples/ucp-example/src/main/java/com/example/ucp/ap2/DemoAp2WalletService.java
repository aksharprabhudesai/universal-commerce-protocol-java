package com.example.ucp.ap2;

import dev.ucomprotocol.ap2.Ap2MandateService;
import dev.ucomprotocol.ap2.JwkKey;
import dev.ucomprotocol.model.Checkout;
import dev.ucomprotocol.spi.CommerceAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Simulated user/platform AP2 wallet active only when {@code ucp.ap2.enabled=true}.
 * <p>
 * Holds a demo EC P-256 key pair ({@code demo-agent-wallet-1}), registers its
 * public JWK with the merchant's {@link Ap2MandateService}, and mints
 * spec-compliant {@code SD-JWT+kb} ({@code dc+sd-jwt}) checkout mandates so
 * developers and AI agents can test the full AP2 cryptographic handshake locally.
 */
@Service
@ConditionalOnProperty(prefix = "ucp.ap2", name = "enabled", havingValue = "true")
public class DemoAp2WalletService {

    public static final String DEMO_AGENT_KID = "demo-agent-wallet-1";

    private final Ap2MandateService ap2MandateService;
    private final CommerceAdapter commerceAdapter;
    private final ECPrivateKey agentPrivateKey;
    private final ECPublicKey agentPublicKey;
    private final JwkKey agentPublicJwk;

    public DemoAp2WalletService(Ap2MandateService ap2MandateService, CommerceAdapter commerceAdapter) {
        this.ap2MandateService = ap2MandateService;
        this.commerceAdapter = commerceAdapter;
        KeyPair kp = Ap2MandateService.generateEcKeyPair("ES256");
        this.agentPrivateKey = (ECPrivateKey) kp.getPrivate();
        this.agentPublicKey = (ECPublicKey) kp.getPublic();
        this.agentPublicJwk = Ap2MandateService.toPublicJwk(agentPublicKey, DEMO_AGENT_KID, "ES256");
        this.ap2MandateService.registerTrustedPlatformKey(agentPublicJwk);
    }

    public JwkKey getAgentPublicJwk() {
        return agentPublicJwk;
    }

    public Map<String, Object> signCheckoutMandate(String checkoutId) {
        Checkout signedCheckout = commerceAdapter.getCheckoutAdapter()
                .getCheckout(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout session not found: " + checkoutId));

        String mandate = ap2MandateService.mintCheckoutMandate(
                signedCheckout,
                agentPrivateKey,
                agentPublicKey,
                DEMO_AGENT_KID,
                Duration.ofMinutes(10));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("checkoutId", checkoutId);
        response.put("agentKeyId", DEMO_AGENT_KID);
        response.put("checkoutMandate", mandate);
        response.put("ap2", Checkout.Ap2Data.builder()
                .merchantAuthorization(signedCheckout.getAp2() != null
                        ? signedCheckout.getAp2().getMerchantAuthorization()
                        : null)
                .checkoutMandate(mandate)
                .build());
        return response;
    }
}
