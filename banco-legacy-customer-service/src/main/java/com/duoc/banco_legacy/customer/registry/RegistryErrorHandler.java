package com.duoc.banco_legacy.customer.registry;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CustomerRegistryController.class)
public class RegistryErrorHandler {
    public record Error(String code, String message) {}
    @ExceptionHandler(RegistryException.class)
    ResponseEntity<Error> domain(RegistryException failure) {
        return ResponseEntity.status(failure.status()).body(new Error(failure.code(), failure.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Error> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST", "Solicitud inválida"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> conflict() {
        return ResponseEntity.status(409).body(new Error("CONFLICT", "Conflicto de persistencia"));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Error> unavailable() {
        return ResponseEntity.status(503).body(new Error("DATABASE_UNAVAILABLE", "Persistencia no disponible"));
    }
}