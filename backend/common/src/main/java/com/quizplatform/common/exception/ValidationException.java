package com.quizplatform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when input validation fails.
 * Returns HTTP 400 Bad Request with details about validation errors.
 */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends BaseException {

    private final List<String> errors;

    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        this.errors = Collections.singletonList(message);
    }

    public ValidationException(String message, List<String> errors) {
        super(message, "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        this.errors = errors != null ? errors : Collections.emptyList();
    }
}
