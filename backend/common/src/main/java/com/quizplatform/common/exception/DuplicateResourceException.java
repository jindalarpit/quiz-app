package com.quizplatform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when attempting to create a resource that already exists.
 * Used for duplicate nicknames, duplicate answer submissions, etc.
 * Returns HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends BaseException {

    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_RESOURCE", HttpStatus.CONFLICT);
    }

    public DuplicateResourceException(String resourceType, String identifier) {
        super(
            String.format("%s already exists: %s", resourceType, identifier),
            "DUPLICATE_RESOURCE",
            HttpStatus.CONFLICT
        );
    }
}
