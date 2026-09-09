package com.duoc.banco_legacy.core.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(long accountId) {
        super("No existe información procesada para la cuenta " + accountId);
    }
}
