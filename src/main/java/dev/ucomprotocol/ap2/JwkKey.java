package dev.ucomprotocol.ap2;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public JSON Web Key (JWK, RFC 7517 &amp; RFC 8037) published in
 * {@code /.well-known/ucp} {@code keys[]} or configured as a trusted platform
 * signing key for UCP signature and AP2 mandate verification.
 * <p>
 * Supports both NIST EC keys ({@code kty="EC"}, {@code crv="P-256"|"P-384"|"P-521"})
 * and Octet Key Pair Ed25519 keys ({@code kty="OKP"}, {@code crv="Ed25519"},
 * where {@code y} is {@code null} and omitted from JSON).
 *
 * @param kid Key identifier referenced in JWS or HTTP Signature headers.
 * @param kty Key type ({@code EC} or {@code OKP}).
 * @param crv Curve name ({@code P-256}, {@code P-384}, {@code P-521}, or {@code Ed25519}).
 * @param x   Base64url-encoded X coordinate (or raw 32-byte Ed25519 public key).
 * @param y   Base64url-encoded Y coordinate for {@code EC} keys ({@code null} for {@code OKP}).
 * @param use Public key use (typically {@code sig}).
 * @param alg Intended JWA signature algorithm ({@code ES256}, {@code ES384}, {@code ES512}, or {@code EdDSA}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JwkKey(
        String kid,
        String kty,
        String crv,
        String x,
        String y,
        String use,
        String alg) {

    /**
     * Creates an {@code OKP} ({@code Ed25519} / {@code EdDSA}) public JWK per
     * RFC 8037.
     *
     * @param kid key identifier
     * @param x   base64url-encoded 32-byte Ed25519 public key
     * @return {@link JwkKey} with {@code kty="OKP"}, {@code crv="Ed25519"}, and {@code alg="EdDSA"}
     */
    public static JwkKey ed25519(String kid, String x) {
        return new JwkKey(kid, "OKP", "Ed25519", x, null, "sig", "EdDSA");
    }

    /**
     * Creates an {@code EC} public JWK per RFC 7517.
     *
     * @param kid key identifier
     * @param crv NIST curve name ({@code P-256}, {@code P-384}, or {@code P-521})
     * @param x   base64url-encoded X coordinate
     * @param y   base64url-encoded Y coordinate
     * @param alg JWS algorithm ({@code ES256}, {@code ES384}, or {@code ES512})
     * @return {@link JwkKey} with {@code kty="EC"}
     */
    public static JwkKey ec(String kid, String crv, String x, String y, String alg) {
        return new JwkKey(kid, "EC", crv, x, y, "sig", alg);
    }
}
