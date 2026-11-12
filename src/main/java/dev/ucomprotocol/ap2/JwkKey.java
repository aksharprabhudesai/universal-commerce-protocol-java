package dev.ucomprotocol.ap2;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public JSON Web Key (JWK, RFC 7517) published in {@code /.well-known/ucp}
 * {@code keys[]} or configured as a trusted platform signing key for AP2
 * mandate verification.
 *
 * @param kid Key identifier referenced in JWS headers.
 * @param kty Key type (e.g., {@code EC}).
 * @param crv Elliptic curve name (e.g., {@code P-256}, {@code P-384}, {@code P-521}).
 * @param x   Base64url-encoded X coordinate.
 * @param y   Base64url-encoded Y coordinate (for EC keys).
 * @param use Public key use (typically {@code sig}).
 * @param alg Intended JWS algorithm (e.g., {@code ES256}).
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
}
