package dev.ucomprotocol.ap2;

import java.util.Objects;

/**
 * Exception raised when AP2 mandate enforcement or cryptographic verification
 * fails during checkout processing.
 */
public class Ap2MandateException extends RuntimeException {

    private final Ap2ErrorCode errorCode;

    /**
     * Creates a new {@link Ap2MandateException}.
     *
     * @param errorCode the canonical AP2 error code
     * @param message   descriptive detail message
     */
    public Ap2MandateException(Ap2ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    /**
     * Creates a new {@link Ap2MandateException} with an underlying cause.
     *
     * @param errorCode the canonical AP2 error code
     * @param message   descriptive detail message
     * @param cause     root cause exception
     */
    public Ap2MandateException(Ap2ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    /**
     * Returns the canonical AP2 protocol error code associated with this failure.
     *
     * @return the AP2 error code
     */
    public Ap2ErrorCode getErrorCode() {
        return errorCode;
    }
}
