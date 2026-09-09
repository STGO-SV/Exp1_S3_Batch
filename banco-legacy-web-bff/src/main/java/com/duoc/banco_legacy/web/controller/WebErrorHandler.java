package com.duoc.banco_legacy.web.controller;

import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WebErrorHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse accountNotFound(AccountNotFoundException exception) {
        return new ErrorResponse("ACCOUNT_NOT_FOUND", exception.getMessage());
    }

    record ErrorResponse(String code, String message) {
    }
}
