package com.quizplatform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user does not have permission to perform an action.
 * Returns HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", HttpStatus.FORBIDDEN);
    }

    public ForbiddenException() {
        super("You do not have permission to perform this action", "FORBIDDEN", HttpStatus.FORBIDDEN);
    }
}
