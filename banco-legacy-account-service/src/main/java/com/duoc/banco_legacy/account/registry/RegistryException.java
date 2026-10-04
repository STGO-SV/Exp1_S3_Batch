package com.duoc.banco_legacy.account.registry;
import org.springframework.http.HttpStatus;
public class RegistryException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public RegistryException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static RegistryException missing(String code) {
        return new RegistryException(HttpStatus.NOT_FOUND, code, "El registro solicitado no existe");
    }
    public static RegistryException conflict(String code) {
        return new RegistryException(HttpStatus.CONFLICT, code, "La operación entra en conflicto con el estado actual");
    }
}