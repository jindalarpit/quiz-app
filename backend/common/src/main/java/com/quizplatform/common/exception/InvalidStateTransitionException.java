package com.quizplatform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Set;

/**
 * Exception thrown when an invalid session state transition is attempted.
 * Returns HTTP 409 Conflict with details about the current state and allowed transitions.
 */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidStateTransitionException extends BaseException {

    private final String currentState;
    private final String attemptedState;
    private final Set<String> allowedTransitions;

    public InvalidStateTransitionException(String currentState, String attemptedState, Set<String> allowedTransitions) {
        super(
            String.format(
                "Invalid state transition from %s to %s. Allowed transitions: %s",
                currentState, attemptedState, allowedTransitions
            ),
            "INVALID_STATE_TRANSITION",
            HttpStatus.CONFLICT
        );
        this.currentState = currentState;
        this.attemptedState = attemptedState;
        this.allowedTransitions = allowedTransitions;
    }
}
