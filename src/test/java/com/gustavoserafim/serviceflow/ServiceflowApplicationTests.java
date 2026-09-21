package com.gustavoserafim.serviceflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste "de fumaça": só garante que o contexto do Spring sobe sem erros
 * (ou seja, todas as configurações e conexões estão corretas). É o primeiro
 * teste que todo projeto Spring Boot tem, e já serve como sanity check da
 * Etapa 1: se o banco não estiver acessível ou alguma configuração estiver
 * errada, este teste falha.
 */
@SpringBootTest
class ServiceflowApplicationTests {

    @Test
    void contextLoads() {
    }

}
