package com.ironbucket.buzzlevane.error;

import com.ironbucket.pactumscroll.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
        IllegalArgumentException ex,
        HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
        SecurityException ex,
        HttpServletRequest request
    ) {
        return build(HttpStatus.FORBIDDEN, ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
        Exception ex,
        HttpServletRequest request
    ) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        String traceId = MDC.get("traceId");

        if (status.is4xxClientError()) {
            LOGGER.warn("Request failed with client error: status={}, path={}, correlationId={}, message={}",
                status.value(), request.getRequestURI(), correlationId, ex.getMessage());
        } else {
            LOGGER.error("Request failed with server error: status={}, path={}, correlationId={}",
                status.value(), request.getRequestURI(), correlationId, ex);
        }

        ApiErrorResponse payload = new ApiErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            ex.getMessage() == null ? "Unexpected error" : ex.getMessage(),
            request.getRequestURI(),
            correlationId,
            traceId
        );

        return ResponseEntity.status(status)
            .header(CORRELATION_ID_HEADER, correlationId)
            .header(REQUEST_ID_HEADER, correlationId)
            .body(payload);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(REQUEST_ID_HEADER);
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }
}