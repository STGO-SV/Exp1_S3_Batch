package com.duoc.banco_legacy.web.client;

public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(Throwable cause) { super("Account Service no disponible", cause); }
}
