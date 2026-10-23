package com.albaraka.backend.services;

/**
 * A call to the external RAG service that failed in a way the caller must be
 * able to explain to the operator.
 *
 * <p>{@code reason} is the stable machine code the admin UI shows verbatim
 * ({@code raglab_not_configured}, {@code raglab_unreachable}, …), {@code status}
 * is the HTTP status the edge should answer with, and {@code context} carries
 * the address we were talking to.
 */
public class RagLabException extends RuntimeException {

    public static final String NOT_CONFIGURED = "raglab_not_configured";
    public static final String UNREACHABLE = "raglab_unreachable";
    public static final String BAD_RESPONSE = "raglab_bad_response";

    private final String reason;
    private final int status;
    private final String context;

    public RagLabException(int status, String reason, String message) {
        this(status, reason, message, null);
    }

    public RagLabException(int status, String reason, String message, String context) {
        super(message);
        this.status = status;
        this.reason = reason;
        this.context = context;
    }

    public String getReason() { return reason; }

    public int getStatus() { return status; }

    public String getContext() { return context; }
}
