package com.ecommerce.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException exception) {
        log.warn("Request failed: {}", exception.getReason());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", exception.getReason() == null ? "Request failed" : exception.getReason());
        response.put("status", exception.getStatusCode().value());
        return ResponseEntity.status(exception.getStatusCode()).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeException(MaxUploadSizeExceededException exception) {
        log.warn("File upload size exceeded: {}", exception.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "File too large. Maximum upload size is 10MB per file.");
        response.put("status", 413);
        return ResponseEntity.status(413).body(response);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPartException(MissingServletRequestPartException exception) {
        log.warn("Missing request part: {}", exception.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "Missing required file part in request");
        response.put("status", 400);
        return ResponseEntity.status(400).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParamException(MissingServletRequestParameterException exception) {
        log.warn("Missing request parameter: {}", exception.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "Missing required parameter: " + exception.getParameterName());
        response.put("status", 400);
        return ResponseEntity.status(400).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException exception) {
        log.error("Illegal state: {}", exception.getMessage(), exception);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", exception.getMessage() == null ? "Request failed" : exception.getMessage());
        response.put("status", 500);
        return ResponseEntity.status(500).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("Invalid argument: {}", exception.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", exception.getMessage() == null ? "Invalid request" : exception.getMessage());
        response.put("status", 400);
        return ResponseEntity.status(400).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception exception) {
        log.error("Unhandled exception: {}", exception.getMessage(), exception);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "Something went wrong. Please try again later.");
        response.put("status", 500);
        return ResponseEntity.status(500).body(response);
    }
}