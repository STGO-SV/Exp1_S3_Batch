package com.duoc.banco_legacy.account.controller;

import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountServiceErrorHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound(AccountNotFoundException exception) {
        return new ErrorResponse("ACCOUNT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErrorResponse unavailable() {
        return new ErrorResponse("DATABASE_UNAVAILABLE", "La información bancaria no está disponible temporalmente");
    }

    record ErrorResponse(String code, String message) {}
}
