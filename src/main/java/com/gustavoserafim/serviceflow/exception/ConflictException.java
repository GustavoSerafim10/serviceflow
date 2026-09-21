package com.gustavoserafim.serviceflow.exception;

/**
 * Lançada quando a operação viola uma regra de negócio por conflito com o
 * estado atual (ex: nome de categoria duplicado). Vira HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
