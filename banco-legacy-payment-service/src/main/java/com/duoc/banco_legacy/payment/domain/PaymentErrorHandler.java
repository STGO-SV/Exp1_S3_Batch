package com.duoc.banco_legacy.payment.domain;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestControllerAdvice
public class PaymentErrorHandler {
 public record Error(String code,String message){}
 @ExceptionHandler(PaymentException.class)
 ResponseEntity<Error> domain(PaymentException failure){return ResponseEntity.status(failure.status()).body(new Error(failure.code(),failure.getMessage()));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.bind.MissingRequestHeaderException.class,
  org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 ResponseEntity<Error> invalid(Exception failure){return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST","Solicitud inválida"));}
 @ExceptionHandler(org.springframework.dao.DataAccessException.class)
 ResponseEntity<Error> database(){return ResponseEntity.status(503).body(new Error("DATABASE_UNAVAILABLE","Persistencia no disponible"));}
}
