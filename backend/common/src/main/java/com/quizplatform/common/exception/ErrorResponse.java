package com.quizplatform.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standardized error response returned by all services.
 * Provides a consistent JSON structure for API error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private Instant timestamp;
    private int status;
    private String errorCode;
    private String message;
    private List<String> errors;
    private String path;

    public static ErrorResponse of(int status, String errorCode, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .build();
    }

    public static ErrorResponse of(int status, String errorCode, String message, List<String> errors, String path) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .errors(errors)
                .path(path)
                .build();
    }
}
