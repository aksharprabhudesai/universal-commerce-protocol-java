package dev.ucomprotocol.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a checkout session in the Universal Commerce Protocol
 * ({@code dev.ucp.shopping.checkout}), optionally extended with AP2
 * cryptographic mandate metadata ({@code dev.ucp.common.payment.ap2_mandate}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Checkout {
    /** Unique identifier of the checkout session. */
    private String id;

    /** ID of the customer or buyer associated with this checkout. */
    private String customerId;

    /** ISO 4217 currency code for the checkout totals. */
    private String currency;

    /** Current lifecycle state of the checkout session. */
    private CheckoutStatus status;

    /** Line items included in the checkout session. */
    @Builder.Default
    private List<Cart.CartItem> items = new ArrayList<>();

    /** Total monetary amount of the checkout session. */
    private BigDecimal totalAmount;

    /** URL for checkout handoff and session recovery when escalation is required. */
    private String continueUrl;

    /** Expiration timestamp of the checkout session. */
    private Instant expiresAt;

    /** ID of the order created once the checkout completes. */
    private String orderId;

    /**
     * Optional AP2 protocol extension payload carrying merchant authorization
     * signatures and checkout mandates.
     */
    private Ap2Data ap2;

    /**
     * AP2 mandate extension data ({@code dev.ucp.common.payment.ap2_mandate}).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Ap2Data {
        /**
         * Detached JWS ({@code <header>..<signature>}) signed by the merchant
         * over the JCS-canonicalized checkout payload (excluding {@code ap2}).
         */
        @JsonProperty("merchant_authorization")
        private String merchantAuthorization;

        /**
         * Verifiable credential ({@code SD-JWT+kb}) signed by the user or
         * trusted platform authorizing completion of this specific checkout.
         */
        @JsonProperty("checkout_mandate")
        private String checkoutMandate;
    }

    /**
     * Checkout lifecycle states defined by the UCP checkout specification.
     */
    public enum CheckoutStatus {
        /** Missing required buyer, shipping, or payment information. */
        INCOMPLETE,
        /** Requires user interaction via {@code continueUrl}. */
        REQUIRES_ESCALATION,
        /** All prerequisites met and ready to be completed. */
        READY_FOR_COMPLETE,
        /** Completion is currently being processed. */
        COMPLETE_IN_PROGRESS,
        /** Checkout completed and order created. */
        COMPLETED,
        /** Checkout session was canceled. */
        CANCELED
    }
}
