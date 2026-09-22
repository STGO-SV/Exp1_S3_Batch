package com.duoc.banco_legacy.web.client;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(long accountId) {
        super("No existe información procesada para la cuenta " + accountId);
    }
}
