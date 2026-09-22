package com.duoc.banco_legacy.atm.controller;

import com.duoc.banco_legacy.atm.service.WithdrawalRejectedException;
import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import com.duoc.banco_legacy.atm.client.AccountServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AtmErrorHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse accountNotFound(AccountNotFoundException exception) {
        return new ErrorResponse("ACCOUNT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErrorResponse unavailable() {
        return new ErrorResponse("ACCOUNT_SERVICE_UNAVAILABLE",
                "La información bancaria no está disponible temporalmente");
    }

    @ExceptionHandler({WithdrawalRejectedException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidWithdrawal(Exception exception) {
        return new ErrorResponse("WITHDRAWAL_REJECTED", exception instanceof WithdrawalRejectedException ? exception.getMessage() : "Monto inválido: use un valor positivo con hasta dos decimales");
    }

    record ErrorResponse(String code, String message) {
    }
}
