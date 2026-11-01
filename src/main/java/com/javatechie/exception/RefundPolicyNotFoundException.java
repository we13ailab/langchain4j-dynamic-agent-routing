package com.javatechie.exception;

import dev.langchain4j.exception.ToolErrorVisibleToLlm;

/**
 * Thrown when no refund policy is defined for a given order status.
 * <p>
 * Implements {@link ToolErrorVisibleToLlm} so that, when it escapes a tool,
 * the {@code ToolExecutionErrorHandler} sends only the curated
 * {@link #messageForLlm()} text to the LLM — never internal details.
 */
public class RefundPolicyNotFoundException extends RuntimeException implements ToolErrorVisibleToLlm {

    private final String orderStatus;

    public RefundPolicyNotFoundException(String orderStatus) {
        super("Refund policy not found for: " + orderStatus);
        this.orderStatus = orderStatus;
    }

    @Override
    public String messageForLlm() {
        return "No refund policy is defined for orders with status \"" + orderStatus
                + "\". Tell the user their request needs manual review by support.";
    }
}
