package com.javatechie.exception;

import dev.langchain4j.exception.ToolErrorVisibleToLlm;

/**
 * Thrown when no shipment tracking record exists for a given order
 * (for example because the order has not been shipped yet).
 * <p>
 * Implements {@link ToolErrorVisibleToLlm} so that, when it escapes a tool,
 * the {@code ToolExecutionErrorHandler} sends only the curated
 * {@link #messageForLlm()} text to the LLM — never internal details.
 */
public class ShipmentTrackingNotFoundException extends RuntimeException implements ToolErrorVisibleToLlm {

    private final String orderId;

    public ShipmentTrackingNotFoundException(String orderId) {
        super("Shipment tracking not found for order: " + orderId);
        this.orderId = orderId;
    }

    @Override
    public String messageForLlm() {
        return "There is no shipment tracking information for order " + orderId
                + ". The order may not have been shipped yet. Tell the user to "
                + "check the order status or contact support if they expected a shipment.";
    }
}
