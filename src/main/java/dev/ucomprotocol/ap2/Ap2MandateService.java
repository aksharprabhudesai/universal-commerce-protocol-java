package dev.ucomprotocol.ap2;

import dev.ucomprotocol.model.Cart;
import dev.ucomprotocol.model.Checkout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cryptographic service implementing the UCP {@code 2026-08-25} AP2 Mandates
 * extension ({@code dev.ucp.common.payment.ap2_mandate}).
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Exporting the merchant's public signing key as an RFC 7517 {@link JwkKey}
 * for {@code /.well-known/ucp} {@code keys[]}.</li>
 * <li>Signing {@link Checkout} payloads using RFC 8785 JCS and detached JWS
 * ({@code ap2.merchant_authorization}).</li>
 * <li>Verifying incoming {@code SD-JWT+kb} {@code ap2.checkout_mandate}
 * credentials, expiration, embedded merchant authorization, and checkout scope
 * consistency.</li>
 * </ul>
 */
public class Ap2MandateService {

    private static final Base64.Encoder B64_URL_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DEC = Base64.getUrlDecoder();

    private final String keyId;
    private final String algorithm;
    private final ECPrivateKey merchantPrivateKey;
    private final ECPublicKey merchantPublicKey;
    private final boolean requireCheckoutMandate;
    private final boolean verifyMandateSignature;
    private final boolean allowEmbeddedAgentJwk;
    private final Map<String, ECPublicKey> trustedPlatformKeys = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;

    /**
     * Creates an {@link Ap2MandateService} with explicit key material and policy
     * flags.
     *
     * @param keyId                  merchant JWK {@code kid}
     * @param algorithm              JWS ECDSA algorithm ({@code ES256},
     *                               {@code ES384}, or {@code ES512})
     * @param merchantPrivateKey     merchant EC private key for signing checkouts
     * @param merchantPublicKey      merchant EC public key for verification and
     *                               JWK publication
     * @param requireCheckoutMandate whether {@code completeCheckout} requires
     *                               {@code ap2.checkout_mandate}
     * @param verifyMandateSignature whether to cryptographically verify mandate
     *                               signatures
     * @param allowEmbeddedAgentJwk  whether to accept self-contained agent JWKs in
     *                               mandate headers or {@code cnf.jwk} claims
     * @param trustedKeys            pre-configured trusted platform public JWKs
     */
    public Ap2MandateService(
            String keyId,
            String algorithm,
            ECPrivateKey merchantPrivateKey,
            ECPublicKey merchantPublicKey,
            boolean requireCheckoutMandate,
            boolean verifyMandateSignature,
            boolean allowEmbeddedAgentJwk,
            List<JwkKey> trustedKeys) {
        this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
        this.algorithm = normalizeAlgorithm(algorithm);
        this.merchantPrivateKey = Objects.requireNonNull(merchantPrivateKey, "merchantPrivateKey must not be null");
        this.merchantPublicKey = Objects.requireNonNull(merchantPublicKey, "merchantPublicKey must not be null");
        this.requireCheckoutMandate = requireCheckoutMandate;
        this.verifyMandateSignature = verifyMandateSignature;
        this.allowEmbeddedAgentJwk = allowEmbeddedAgentJwk;
        this.jsonMapper = JsonMapper.builder().build();

        if (trustedKeys != null) {
            for (JwkKey jwk : trustedKeys) {
                if (jwk != null && jwk.kid() != null) {
                    this.trustedPlatformKeys.put(jwk.kid(), parseEcPublicKeyFromJwk(jwk));
                }
            }
        }
    }

    /**
     * Generates a fresh EC key pair matching the specified JWS algorithm
     * ({@code ES256}, {@code ES384}, or {@code ES512}).
     *
     * @param algorithm JWS algorithm name
     * @return generated EC {@link KeyPair}
     */
    public static KeyPair generateEcKeyPair(String algorithm) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec(stdCurveName(normalizeAlgorithm(algorithm))));
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to generate EC key pair for " + algorithm, e);
        }
    }

    /**
     * Parses a PKCS#8 PEM or Base64-encoded EC private key.
     *
     * @param pemOrBase64 PKCS#8 private key text
     * @return parsed {@link ECPrivateKey}
     */
    public static ECPrivateKey parsePrivateKey(String pemOrBase64) {
        try {
            String stripped = pemOrBase64
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(stripped);
            PrivateKey key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            return (ECPrivateKey) key;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid PKCS#8 EC private key configuration", e);
        }
    }

    /**
     * Parses an X.509 PEM or Base64-encoded EC public key.
     *
     * @param pemOrBase64 X.509 public key text
     * @return parsed {@link ECPublicKey}
     */
    public static ECPublicKey parsePublicKey(String pemOrBase64) {
        try {
            String stripped = pemOrBase64
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(stripped);
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
            return (ECPublicKey) key;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid X.509 EC public key configuration", e);
        }
    }

    /**
     * Converts an {@link ECPublicKey} into a public {@link JwkKey}.
     *
     * @param publicKey EC public key
     * @param kid       key identifier
     * @param alg       JWS algorithm (e.g., {@code ES256})
     * @return public {@link JwkKey} record
     */
    public static JwkKey toPublicJwk(ECPublicKey publicKey, String kid, String alg) {
        String normalizedAlg = normalizeAlgorithm(alg);
        int coordBytes = coordinateSize(normalizedAlg);
        ECPoint point = publicKey.getW();
        String x = B64_URL_ENC.encodeToString(toFixedUnsignedBytes(point.getAffineX(), coordBytes));
        String y = B64_URL_ENC.encodeToString(toFixedUnsignedBytes(point.getAffineY(), coordBytes));
        return new JwkKey(kid, "EC", jwkCurveName(normalizedAlg), x, y, "sig", normalizedAlg);
    }

    /**
     * Returns the merchant's public signing key as a {@link JwkKey} for
     * {@code /.well-known/ucp} {@code keys[]}.
     *
     * @return merchant public JWK
     */
    public JwkKey getMerchantPublicJwk() {
        return toPublicJwk(merchantPublicKey, keyId, algorithm);
    }

    /**
     * Returns whether checkout completion requires an {@code ap2.checkout_mandate}.
     *
     * @return {@code true} if checkout mandates are strictly required
     */
    public boolean isRequireCheckoutMandate() {
        return requireCheckoutMandate;
    }

    /**
     * Registers an additional trusted platform or agent public JWK at runtime.
     *
     * @param jwk trusted public EC JWK
     */
    public void registerTrustedPlatformKey(JwkKey jwk) {
        Objects.requireNonNull(jwk, "jwk must not be null");
        Objects.requireNonNull(jwk.kid(), "jwk.kid must not be null");
        trustedPlatformKeys.put(jwk.kid(), parseEcPublicKeyFromJwk(jwk));
    }

    /**
     * Computes and attaches {@code ap2.merchant_authorization} (detached JWS over
     * RFC 8785 JCS checkout payload excluding {@code ap2}) onto the given checkout.
     *
     * @param checkout checkout session to sign
     * @return the signed checkout session
     */
    public Checkout signCheckout(Checkout checkout) {
        if (checkout == null) {
            return null;
        }
        String existingMandate = checkout.getAp2() != null ? checkout.getAp2().getCheckoutMandate() : null;
        JsonNode checkoutNode = jsonMapper.valueToTree(checkout);
        byte[] canonicalPayload = JcsCanonicalizer.canonicalizeWithoutField(checkoutNode, "ap2");

        Map<String, String> header = new LinkedHashMap<>();
        header.put("alg", algorithm);
        header.put("kid", keyId);
        String encodedHeader = B64_URL_ENC.encodeToString(
                JcsCanonicalizer.canonicalize(jsonMapper.valueToTree(header)));
        String signingInput = encodedHeader + "." + B64_URL_ENC.encodeToString(canonicalPayload);
        byte[] signature = signBytes(signingInput.getBytes(StandardCharsets.US_ASCII), merchantPrivateKey, algorithm);
        String detachedJws = encodedHeader + ".." + B64_URL_ENC.encodeToString(signature);

        Checkout.Ap2Data ap2Data = Checkout.Ap2Data.builder()
                .merchantAuthorization(detachedJws)
                .checkoutMandate(existingMandate)
                .build();
        checkout.setAp2(ap2Data);
        return checkout;
    }

    /**
     * Verifies the merchant's own {@code ap2.merchant_authorization} detached JWS
     * on a serialized checkout JSON tree.
     *
     * @param checkoutNode JSON representation of a checkout containing {@code ap2}
     */
    public void verifyMerchantAuthorization(JsonNode checkoutNode) {
        if (checkoutNode == null || !checkoutNode.isObject()) {
            throw new Ap2MandateException(Ap2ErrorCode.MERCHANT_AUTHORIZATION_MISSING,
                    "Embedded checkout object is missing");
        }
        JsonNode ap2Node = checkoutNode.get("ap2");
        JsonNode authNode = ap2Node != null ? ap2Node.get("merchant_authorization") : null;
        if (authNode == null || authNode.isNull() || authNode.asString().isBlank()) {
            throw new Ap2MandateException(Ap2ErrorCode.MERCHANT_AUTHORIZATION_MISSING,
                    "Checkout omits ap2.merchant_authorization");
        }
        String jws = authNode.asString();
        String[] parts = jws.split("\\.", -1);
        if (parts.length != 3 || !parts[1].isEmpty() || parts[0].isEmpty() || parts[2].isEmpty()) {
            throw new Ap2MandateException(Ap2ErrorCode.MERCHANT_AUTHORIZATION_INVALID,
                    "Malformed detached JWS in ap2.merchant_authorization");
        }

        JsonNode header = decodeJsonPart(parts[0], Ap2ErrorCode.MERCHANT_AUTHORIZATION_INVALID);
        String alg = textField(header, "alg");
        String kid = textField(header, "kid");
        if (!algorithm.equals(alg) || !keyId.equals(kid)) {
            throw new Ap2MandateException(Ap2ErrorCode.MERCHANT_AUTHORIZATION_INVALID,
                    "Merchant authorization header alg/kid does not match active merchant key");
        }

        byte[] canonicalPayload = JcsCanonicalizer.canonicalizeWithoutField(checkoutNode, "ap2");
        String signingInput = parts[0] + "." + B64_URL_ENC.encodeToString(canonicalPayload);
        boolean valid = verifyBytes(
                signingInput.getBytes(StandardCharsets.US_ASCII),
                decodeBase64Url(parts[2], Ap2ErrorCode.MERCHANT_AUTHORIZATION_INVALID),
                merchantPublicKey,
                algorithm);
        if (!valid) {
            throw new Ap2MandateException(Ap2ErrorCode.MERCHANT_AUTHORIZATION_INVALID,
                    "Merchant authorization signature verification failed");
        }
    }

    /**
     * Validates an incoming {@code ap2} payload against the current checkout
     * session per UCP {@code 2026-08-25}.
     *
     * @param activeCheckout current server-side checkout session state
     * @param submittedAp2   {@code ap2} extension submitted with
     *                       {@code completeCheckout}
     */
    public void verifyCheckoutMandate(Checkout activeCheckout, Checkout.Ap2Data submittedAp2) {
        boolean hasMandate = submittedAp2 != null
                && submittedAp2.getCheckoutMandate() != null
                && !submittedAp2.getCheckoutMandate().isBlank();

        if (!hasMandate) {
            if (requireCheckoutMandate) {
                throw new Ap2MandateException(Ap2ErrorCode.MANDATE_REQUIRED,
                        "AP2 is active: complete_checkout requires ap2.checkout_mandate");
            }
            return;
        }

        String sdJwt = submittedAp2.getCheckoutMandate().trim();
        String[] tildeParts = sdJwt.split("~", -1);
        String issuerJwt = tildeParts[0];
        String[] jwtParts = issuerJwt.split("\\.", -1);
        if (jwtParts.length != 3 || jwtParts[0].isEmpty() || jwtParts[1].isEmpty() || jwtParts[2].isEmpty()) {
            throw new Ap2MandateException(Ap2ErrorCode.MANDATE_INVALID_SIGNATURE,
                    "Malformed SD-JWT in ap2.checkout_mandate");
        }

        JsonNode header = decodeJsonPart(jwtParts[0], Ap2ErrorCode.MANDATE_INVALID_SIGNATURE);
        JsonNode payload = decodeJsonPart(jwtParts[1], Ap2ErrorCode.MANDATE_INVALID_SIGNATURE);

        // 1. Check expiration
        JsonNode expNode = payload.get("exp");
        if (expNode != null && expNode.isNumber()) {
            long nowEpochSeconds = Instant.now().getEpochSecond();
            if (expNode.longValue() < nowEpochSeconds) {
                throw new Ap2MandateException(Ap2ErrorCode.MANDATE_EXPIRED,
                        "ap2.checkout_mandate has expired");
            }
        }

        // 2. Cryptographic signature verification (issuer JWT + optional Key-Binding JWT)
        if (verifyMandateSignature) {
            String alg = textField(header, "alg");
            if (alg == null || alg.isBlank()) {
                throw new Ap2MandateException(Ap2ErrorCode.MANDATE_INVALID_SIGNATURE,
                        "Mandate JWS header omits alg");
            }
            String normalizedAlg = normalizeAlgorithm(alg);
            ECPublicKey verificationKey = resolveMandateVerificationKey(header, payload);

            String signingInput = jwtParts[0] + "." + jwtParts[1];
            byte[] sigBytes = decodeBase64Url(jwtParts[2], Ap2ErrorCode.MANDATE_INVALID_SIGNATURE);
            if (!verifyBytes(signingInput.getBytes(StandardCharsets.US_ASCII), sigBytes, verificationKey, normalizedAlg)) {
                throw new Ap2MandateException(Ap2ErrorCode.MANDATE_INVALID_SIGNATURE,
                        "Mandate issuer JWS signature verification failed");
            }

            // Verify Key-Binding JWT (+kb) if present at the end of the SD-JWT
            String lastTildePart = tildeParts.length > 1 ? tildeParts[tildeParts.length - 1] : "";
            if (!lastTildePart.isEmpty() && lastTildePart.contains(".")) {
                verifyKeyBindingJwt(lastTildePart, payload);
            }
        }

        // 3. Extract embedded checkout and verify merchant_authorization + scope match
        JsonNode embeddedCheckout = payload.has("checkout") ? payload.get("checkout") : payload;
        verifyMerchantAuthorization(embeddedCheckout);
        verifyScopeMatchesSession(activeCheckout, embeddedCheckout);
    }

    /**
     * Mints a spec-compliant {@code SD-JWT+kb} ({@code dc+sd-jwt}) checkout mandate
     * over a merchant-signed {@link Checkout}. Useful for integration tests, sample
     * storefronts, and platform/agent SDK workflows.
     *
     * @param signedCheckout  checkout carrying {@code ap2.merchant_authorization}
     * @param agentPrivateKey platform or user EC private key
     * @param agentPublicKey  platform or user EC public key
     * @param agentKid        platform or user key ID
     * @param ttl             validity duration of the mandate
     * @return serialized {@code <issuer-jwt>~<kb-jwt>} checkout mandate string
     */
    public String mintCheckoutMandate(
            Checkout signedCheckout,
            ECPrivateKey agentPrivateKey,
            ECPublicKey agentPublicKey,
            String agentKid,
            Duration ttl) {
        Objects.requireNonNull(signedCheckout, "signedCheckout must not be null");
        JsonNode checkoutNode = jsonMapper.valueToTree(signedCheckout);
        verifyMerchantAuthorization(checkoutNode);

        String alg = curveAlgorithmForKey(agentPublicKey);
        JwkKey agentJwk = toPublicJwk(agentPublicKey, agentKid, alg);
        long now = Instant.now().getEpochSecond();
        long exp = now + (ttl != null ? ttl.toSeconds() : 300L);

        ObjectNode issuerHeader = jsonMapper.createObjectNode();
        issuerHeader.put("alg", alg);
        issuerHeader.put("kid", agentKid);
        issuerHeader.put("typ", "dc+sd-jwt");

        ObjectNode cnf = jsonMapper.createObjectNode();
        cnf.set("jwk", jsonMapper.valueToTree(agentJwk));

        ObjectNode issuerPayload = jsonMapper.createObjectNode();
        issuerPayload.put("iss", "urn:agent:" + agentKid);
        issuerPayload.put("iat", now);
        issuerPayload.put("exp", exp);
        issuerPayload.set("cnf", cnf);
        issuerPayload.set("checkout", checkoutNode);

        String issuerJwt = signCompactJwt(issuerHeader, issuerPayload, agentPrivateKey, alg);

        // Compute sd_hash over "<issuer-jwt>~" and attach Key-Binding JWT (+kb)
        String sdInput = issuerJwt + "~";
        String sdHash = sha256Base64Url(sdInput.getBytes(StandardCharsets.US_ASCII));

        ObjectNode kbHeader = jsonMapper.createObjectNode();
        kbHeader.put("alg", alg);
        kbHeader.put("typ", "kb+jwt");

        ObjectNode kbPayload = jsonMapper.createObjectNode();
        kbPayload.put("iat", now);
        kbPayload.put("aud", keyId);
        kbPayload.put("nonce", signedCheckout.getId());
        kbPayload.put("sd_hash", sdHash);

        String kbJwt = signCompactJwt(kbHeader, kbPayload, agentPrivateKey, alg);
        return issuerJwt + "~" + kbJwt;
    }

    private void verifyKeyBindingJwt(String kbJwt, JsonNode issuerPayload) {
        String[] kbParts = kbJwt.split("\\.", -1);
        if (kbParts.length != 3) {
            throw new Ap2MandateException(Ap2ErrorCode.MANDATE_INVALID_SIGNATURE,
                    "Malformed Key-Binding JWT in ap2.checkout_mandate");
        }
        JsonNode kbHeader = decodeJsonPart(kbParts[0], Ap2ErrorCode.MANDATE_INVALID_SIGNATURE);
        String alg = normalizeAlgorithm(textField(kbHeader, "alg"));

        JsonNode cnfJwkNode = issuerPayload.path("cnf").path("jwk");
        if (cnfJwkNode.isMissingNode() || cnfJwkNode.isNull()) {
            return;
        }
        JwkKey holderJwk = jsonMapper.treeToValue(cnfJwkNode, JwkKey.class);
        ECPublicKey holderKey = parseEcPublicKeyFromJwk(holderJwk);
        String kbSigningInput = kbParts[0] + "." + kbParts[1];
        byte[] kbSig = decodeBase64Url(kbParts[2], Ap2ErrorCode.MANDATE_INVALID_SIGNATURE);
        if (!verifyBytes(kbSigningInput.getBytes(StandardCharsets.US_ASCII), kbSig, holderKey, alg)) {
            throw new Ap2MandateException(Ap2ErrorCode.MANDATE_INVALID_SIGNATURE,
                    "Key-Binding (+kb) JWT signature verification failed");
        }
    }

    private ECPublicKey resolveMandateVerificationKey(JsonNode header, JsonNode payload) {
        String kid = textField(header, "kid");
        if (kid != null && trustedPlatformKeys.containsKey(kid)) {
            return trustedPlatformKeys.get(kid);
        }
        if (allowEmbeddedAgentJwk) {
            JsonNode headerJwk = header.get("jwk");
            if (headerJwk != null && headerJwk.isObject()) {
                return parseEcPublicKeyFromJwk(jsonMapper.treeToValue(headerJwk, JwkKey.class));
            }
            JsonNode cnfJwk = payload.path("cnf").path("jwk");
            if (cnfJwk != null && cnfJwk.isObject()) {
                return parseEcPublicKeyFromJwk(jsonMapper.treeToValue(cnfJwk, JwkKey.class));
            }
        }
        throw new Ap2MandateException(Ap2ErrorCode.AGENT_MISSING_KEY,
                "No trusted public key found for mandate kid=" + kid);
    }

    private void verifyScopeMatchesSession(Checkout activeCheckout, JsonNode embeddedCheckout) {
        String embeddedId = textField(embeddedCheckout, "id");
        if (!Objects.equals(activeCheckout.getId(), embeddedId)) {
            throw new Ap2MandateException(Ap2ErrorCode.MANDATE_SCOPE_MISMATCH,
                    "Mandate checkout id '" + embeddedId + "' does not match session '" + activeCheckout.getId() + "'");
        }

        if (activeCheckout.getCurrency() != null) {
            String embeddedCurrency = textField(embeddedCheckout, "currency");
            if (!Objects.equals(activeCheckout.getCurrency(), embeddedCurrency)) {
                throw new Ap2MandateException(Ap2ErrorCode.MANDATE_SCOPE_MISMATCH,
                        "Mandate currency does not match active checkout session");
            }
        }

        if (activeCheckout.getTotalAmount() != null) {
            JsonNode totalNode = embeddedCheckout.get("totalAmount");
            if (totalNode == null || !totalNode.isNumber()
                    || activeCheckout.getTotalAmount().compareTo(totalNode.decimalValue()) != 0) {
                throw new Ap2MandateException(Ap2ErrorCode.MANDATE_SCOPE_MISMATCH,
                        "Mandate totalAmount does not match active checkout session");
            }
        }

        List<Cart.CartItem> activeItems = activeCheckout.getItems() != null ? activeCheckout.getItems() : List.of();
        JsonNode embeddedItems = embeddedCheckout.get("items");
        int embeddedCount = embeddedItems != null && embeddedItems.isArray() ? embeddedItems.size() : 0;
        if (activeItems.size() != embeddedCount) {
            throw new Ap2MandateException(Ap2ErrorCode.MANDATE_SCOPE_MISMATCH,
                    "Mandate line item count does not match active checkout session");
        }
        for (int i = 0; i < activeItems.size(); i++) {
            Cart.CartItem item = activeItems.get(i);
            JsonNode embItem = embeddedItems.get(i);
            String embProdId = textField(embItem, "productId");
            int embQty = embItem.has("quantity") ? embItem.get("quantity").intValue() : -1;
            if (!Objects.equals(item.getProductId(), embProdId) || item.getQuantity() != embQty) {
                throw new Ap2MandateException(Ap2ErrorCode.MANDATE_SCOPE_MISMATCH,
                        "Mandate line items do not match active checkout session");
            }
        }
    }

    private String signCompactJwt(JsonNode header, JsonNode payload, ECPrivateKey privateKey, String alg) {
        String h = B64_URL_ENC.encodeToString(JcsCanonicalizer.canonicalize(header));
        String p = B64_URL_ENC.encodeToString(JcsCanonicalizer.canonicalize(payload));
        String input = h + "." + p;
        byte[] sig = signBytes(input.getBytes(StandardCharsets.US_ASCII), privateKey, alg);
        return input + "." + B64_URL_ENC.encodeToString(sig);
    }

    private JsonNode decodeJsonPart(String b64Url, Ap2ErrorCode errorCode) {
        try {
            byte[] decoded = decodeBase64Url(b64Url, errorCode);
            return jsonMapper.readTree(decoded);
        } catch (Ap2MandateException e) {
            throw e;
        } catch (Exception e) {
            throw new Ap2MandateException(errorCode, "Failed to parse JWS JSON segment", e);
        }
    }

    private static byte[] decodeBase64Url(String part, Ap2ErrorCode errorCode) {
        try {
            return B64_URL_DEC.decode(part);
        } catch (IllegalArgumentException e) {
            throw new Ap2MandateException(errorCode, "Invalid base64url encoding in JWS", e);
        }
    }

    private static String textField(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asString();
    }

    private static byte[] signBytes(byte[] data, ECPrivateKey privateKey, String alg) {
        try {
            Signature signature = Signature.getInstance(jcaSignatureAlgorithm(alg));
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute AP2 ECDSA signature", e);
        }
    }

    private static boolean verifyBytes(byte[] data, byte[] sigBytes, ECPublicKey publicKey, String alg) {
        try {
            Signature signature = Signature.getInstance(jcaSignatureAlgorithm(alg));
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(sigBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static ECPublicKey parseEcPublicKeyFromJwk(JwkKey jwk) {
        if (jwk == null || !"EC".equalsIgnoreCase(jwk.kty()) || jwk.x() == null || jwk.y() == null) {
            throw new Ap2MandateException(Ap2ErrorCode.AGENT_MISSING_KEY, "Invalid EC JWK definition");
        }
        try {
            String stdCurve = switch (jwk.crv() != null ? jwk.crv() : "P-256") {
                case "P-256", "secp256r1" -> "secp256r1";
                case "P-384", "secp384r1" -> "secp384r1";
                case "P-521", "secp521r1" -> "secp521r1";
                default -> throw new IllegalArgumentException("Unsupported EC curve: " + jwk.crv());
            };
            BigInteger x = new BigInteger(1, B64_URL_DEC.decode(jwk.x()));
            BigInteger y = new BigInteger(1, B64_URL_DEC.decode(jwk.y()));
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(stdCurve));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
            ECPublicKeySpec pubSpec = new ECPublicKeySpec(new ECPoint(x, y), ecSpec);
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(pubSpec);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new Ap2MandateException(Ap2ErrorCode.AGENT_MISSING_KEY, "Failed to reconstruct EC public key from JWK", e);
        }
    }

    private static String sha256Base64Url(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return B64_URL_ENC.encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] toFixedUnsignedBytes(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        if (raw.length == length + 1 && raw[0] == 0) {
            return Arrays.copyOfRange(raw, 1, raw.length);
        }
        if (raw.length < length) {
            byte[] padded = new byte[length];
            System.arraycopy(raw, 0, padded, length - raw.length, raw.length);
            return padded;
        }
        return Arrays.copyOfRange(raw, raw.length - length, raw.length);
    }

    private static String normalizeAlgorithm(String alg) {
        if (alg == null || alg.isBlank()) {
            return "ES256";
        }
        String upper = alg.trim().toUpperCase();
        return switch (upper) {
            case "ES256", "ES384", "ES512" -> upper;
            default -> throw new Ap2MandateException(Ap2ErrorCode.MANDATE_INVALID_SIGNATURE,
                    "Unsupported AP2 JWS algorithm: " + alg);
        };
    }

    private static String jcaSignatureAlgorithm(String alg) {
        return switch (alg) {
            case "ES256" -> "SHA256withECDSAinP1363Format";
            case "ES384" -> "SHA384withECDSAinP1363Format";
            case "ES512" -> "SHA512withECDSAinP1363Format";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        };
    }

    private static String stdCurveName(String alg) {
        return switch (alg) {
            case "ES256" -> "secp256r1";
            case "ES384" -> "secp384r1";
            case "ES512" -> "secp521r1";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        };
    }

    private static String jwkCurveName(String alg) {
        return switch (alg) {
            case "ES256" -> "P-256";
            case "ES384" -> "P-384";
            case "ES512" -> "P-521";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        };
    }

    private static int coordinateSize(String alg) {
        return switch (alg) {
            case "ES256" -> 32;
            case "ES384" -> 48;
            case "ES512" -> 66;
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        };
    }

    private static String curveAlgorithmForKey(ECPublicKey key) {
        int fieldSize = key.getParams().getCurve().getField().getFieldSize();
        if (fieldSize <= 256) {
            return "ES256";
        } else if (fieldSize <= 384) {
            return "ES384";
        }
        return "ES512";
    }
}
