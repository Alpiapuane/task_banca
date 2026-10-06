package com.example.banking.common;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("timestamp", Instant.now(), "status",
                ex.getStatus().value(), "error", ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400, "error", message));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<?> handleBadInput(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400, "error", "Invalid request parameter"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("timestamp", Instant.now(), "status", 500, "error", "Internal server error"));
    }
}
