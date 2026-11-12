package com.holidays.plan.service;

import org.springframework.http.HttpStatus;

/** Errore di business con messaggio sicuro da mostrare all'utente. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }

    public static ApiException bad(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, msg); }

    public static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "Operazione non consentita"); }

    public static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Elemento non trovato"); }

    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, msg); }
}
