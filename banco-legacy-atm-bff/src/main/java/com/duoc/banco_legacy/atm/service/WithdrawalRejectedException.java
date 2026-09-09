package com.duoc.banco_legacy.atm.service;

public class WithdrawalRejectedException extends RuntimeException {
    public WithdrawalRejectedException(String message) {
        super(message);
    }
}
