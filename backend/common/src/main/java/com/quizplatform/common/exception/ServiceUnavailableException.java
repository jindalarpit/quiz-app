package com.quizplatform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an upstream service is unavailable or fails to respond.
 * Returns HTTP 503 Service Unavailable.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends BaseException {

    public ServiceUnavailableException(String message) {
        super(message, "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String service, String detail) {
        super(String.format("Service '%s' is unavailable: %s", service, detail),
                "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, "SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
