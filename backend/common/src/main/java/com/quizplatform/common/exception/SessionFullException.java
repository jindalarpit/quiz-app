package com.quizplatform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a participant attempts to join a session that has
 * reached the maximum capacity of 1000 participants.
 * Returns HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SessionFullException extends BaseException {

    public SessionFullException(String pin) {
        super(
            String.format("Session %s has reached maximum capacity", pin),
            "SESSION_FULL",
            HttpStatus.CONFLICT
        );
    }
}
