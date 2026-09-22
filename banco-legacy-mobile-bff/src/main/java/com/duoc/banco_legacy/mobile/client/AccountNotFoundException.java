package com.duoc.banco_legacy.mobile.client;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(long accountId) {
        super("Cuenta no encontrada: " + accountId);
    }
}
