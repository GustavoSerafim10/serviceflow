package com.gustavoserafim.serviceflow.exception;

/**
 * Lançada quando o pedido é válido em formato, mas viola uma regra de negócio
 * (ex: transição de status inválida, categoria inativa). Vira HTTP 422.
 *
 * Diferença para os outros: 400 = dado malformado; 409 = conflito com algo que
 * já existe; 422 = "entendi o pedido, mas o negócio não permite".
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
