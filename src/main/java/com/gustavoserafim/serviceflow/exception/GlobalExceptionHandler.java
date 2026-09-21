package com.gustavoserafim.serviceflow.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento global de exceções.
 *
 * @RestControllerAdvice = "conselheiro" de TODOS os controllers: quando uma
 * exceção escapa de qualquer controller/service, o Spring procura aqui um
 * método @ExceptionHandler compatível. Assim controllers e services só
 * lançam exceções e não se preocupam com HTTP; e toda resposta de erro da
 * API tem o mesmo formato.
 *
 * O formato é ProblemDetail (RFC 7807 / 9457), padrão nativo do Spring 6:
 * { "type", "title", "status", "detail", "instance", ...extras }.
 *
 * Estende ResponseEntityExceptionHandler, que já trata em ProblemDetail as
 * exceções padrão do Spring MVC (JSON malformado -> 400, método HTTP errado
 * -> 405, Content-Type errado -> 415...). Sem isso, o handler genérico
 * (Exception.class) abaixo transformaria todas elas em 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso não encontrado");
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflito");
        return problem;
    }

    // Rede de segurança: se duas requisições passarem juntas pela checagem do
    // Service, o índice único do banco barra a segunda e cai aqui.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade no banco", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A operação viola uma restrição de integridade dos dados");
        problem.setTitle("Conflito");
        return problem;
    }

    // Login com senha errada / usuário inexistente / usuário desativado.
    // Mensagem única e genérica de propósito: não revelar QUAL dos dois falhou
    // (evita que alguém descubra quais e-mails existem no sistema).
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        problem.setTitle("Não autenticado");
        return problem;
    }

    // Lançada pelo @PreAuthorize quando o usuário logado não tem a role exigida.
    // Precisa ser tratada aqui: sem este método, cairia no handler genérico (500).
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Você não tem permissão para acessar este recurso");
        problem.setTitle("Acesso negado");
        return problem;
    }

    // Disparada pelo @Valid quando o DTO de entrada é inválido. Sobrescreve o
    // comportamento padrão da classe-pai para incluir o mapa campo -> mensagem.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos");
        problem.setTitle("Erro de validação");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // Último recurso: erro inesperado. Registra o detalhe no log, mas NÃO o
    // devolve ao cliente (não vazar stack trace / detalhes internos).
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente mais tarde.");
        problem.setTitle("Erro interno");
        return problem;
    }
}
