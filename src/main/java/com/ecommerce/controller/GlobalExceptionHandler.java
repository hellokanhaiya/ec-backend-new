package com.ecommerce.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", exception.getReason() == null ? "Request failed" : exception.getReason());
        response.put("status", exception.getStatusCode().value());
        return ResponseEntity.status(exception.getStatusCode()).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", exception.getMessage() == null ? "Request failed" : exception.getMessage());
        response.put("status", 500);
        return ResponseEntity.status(500).body(response);
    }
}
