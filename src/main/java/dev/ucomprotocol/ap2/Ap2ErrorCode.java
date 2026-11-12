package dev.ucomprotocol.ap2;

/**
 * Canonical error codes defined by the UCP {@code dev.ucp.common.payment.ap2_mandate}
 * specification ({@code 2026-08-25}).
 */
public enum Ap2ErrorCode {
    /** AP2 is active on the checkout session, but {@code ap2.checkout_mandate} was omitted. */
    MANDATE_REQUIRED("mandate_required"),

    /** No public signing key could be resolved to verify the agent/platform mandate. */
    AGENT_MISSING_KEY("agent_missing_key"),

    /** The cryptographic signature on {@code ap2.checkout_mandate} failed verification. */
    MANDATE_INVALID_SIGNATURE("mandate_invalid_signature"),

    /** The mandate's {@code exp} timestamp has already passed. */
    MANDATE_EXPIRED("mandate_expired"),

    /** The checkout embedded inside the mandate does not match the active checkout session. */
    MANDATE_SCOPE_MISMATCH("mandate_scope_mismatch"),

    /** The {@code ap2.merchant_authorization} signature inside the mandate is invalid. */
    MERCHANT_AUTHORIZATION_INVALID("merchant_authorization_invalid"),

    /** The checkout embedded in the mandate omits {@code ap2.merchant_authorization}. */
    MERCHANT_AUTHORIZATION_MISSING("merchant_authorization_missing");

    private final String code;

    Ap2ErrorCode(String code) {
        this.code = code;
    }

    /**
     * Returns the wire-format UCP error code string.
     *
     * @return the snake_case protocol error code
     */
    public String getCode() {
        return code;
    }
}
