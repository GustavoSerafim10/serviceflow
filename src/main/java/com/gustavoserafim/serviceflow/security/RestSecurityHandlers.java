package com.gustavoserafim.serviceflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Respostas de erro geradas DENTRO da cadeia de filtros do Security.
 *
 * Por que existe se já temos o GlobalExceptionHandler? Porque o @RestControllerAdvice
 * só enxerga exceções lançadas dentro de controllers. Um 401 (sem token) ou
 * 403 (URL proibida) acontece nos filtros, ANTES do controller — então
 * precisamos escrever o JSON à mão, no mesmo formato ProblemDetail.
 *
 *  - 401 Unauthorized  = "não sei quem você é" (sem token / token inválido)
 *  - 403 Forbidden     = "sei quem você é, mas você não pode fazer isso"
 */
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Token ausente, inválido ou expirado");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Acesso negado",
                "Você não tem permissão para acessar este recurso");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
