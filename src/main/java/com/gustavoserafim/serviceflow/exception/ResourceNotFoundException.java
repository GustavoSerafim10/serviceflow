package com.gustavoserafim.serviceflow.exception;

/**
 * Lançada quando um recurso pedido não existe. O GlobalExceptionHandler
 * a converte em HTTP 404.
 *
 * Estende RuntimeException (exceção "unchecked"): não obriga todo método
 * a declarar "throws", e o Spring reverte a transação automaticamente
 * quando uma RuntimeException sai de um método @Transactional.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " com id " + id + " não foi encontrado(a)");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
