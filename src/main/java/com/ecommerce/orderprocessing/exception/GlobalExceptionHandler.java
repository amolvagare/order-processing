package com.ecommerce.orderprocessing.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler — returns a consistent error response shape for all failures.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Handle order-not-found errors (404)
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle_order_not_found(OrderNotFoundException ex) {
        log.error("Order not found: {}", ex.getMessage());
        return build_error_response(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Handle customer-not-found errors (404)
    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle_customer_not_found(CustomerNotFoundException ex) {
        log.error("Customer not found: {}", ex.getMessage());
        return build_error_response(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Handle address-not-found errors (404)
    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle_address_not_found(AddressNotFoundException ex) {
        log.error("Address not found: {}", ex.getMessage());
        return build_error_response(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Handle duplicate customer email errors (409)
    @ExceptionHandler(CustomerEmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handle_duplicate_customer_email(CustomerEmailAlreadyExistsException ex) {
        log.error("Duplicate customer email: {}", ex.getMessage());
        return build_error_response(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Handle invalid cancellation attempts (400)
    @ExceptionHandler(OrderCancellationException.class)
    public ResponseEntity<Map<String, Object>> handle_cancellation_error(OrderCancellationException ex) {
        log.error("Order cancellation rejected: {}", ex.getMessage());
        return build_error_response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Handle @Valid bean validation errors (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle_validation_error(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.error("Validation failed: {}", errors);
        return build_error_response(HttpStatus.BAD_REQUEST, errors);
    }

    // Catch-all for any unexpected runtime errors (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle_generic_error(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return build_error_response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    // Build a standard error response body
    private ResponseEntity<Map<String, Object>> build_error_response(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
