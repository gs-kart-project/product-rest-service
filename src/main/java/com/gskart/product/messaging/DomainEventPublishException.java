package com.gskart.product.messaging;

public class DomainEventPublishException extends RuntimeException {
    public DomainEventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
