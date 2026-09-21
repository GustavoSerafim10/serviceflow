package com.gustavoserafim.serviceflow;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base dos testes de integração: sobe a aplicação inteira (Spring + Flyway +
 * Security + JPA) contra um PostgreSQL REAL e descartável.
 *
 * - @Testcontainers/@Container: o JUnit inicia um container Postgres antes dos
 *   testes e o destrói depois (requer Docker em execução).
 * - @ServiceConnection: o Spring Boot descobre sozinho a URL/usuário/senha do
 *   container e configura o DataSource. Nenhum application-test.yml.
 * - @AutoConfigureMockMvc: permite chamar os endpoints em memória (MockMvc),
 *   passando por toda a cadeia de filtros de segurança, sem abrir porta de rede.
 *
 * Diferente dos testes unitários (Mockito), aqui nada é simulado: valida que
 * migrations, mapeamentos JPA, segurança e regras funcionam JUNTOS.
 */
// base-url apontando para uma porta sem ninguém ouvindo: os testes de integração nunca dependem
// (nem falam com) o serviço Python, e verificam que a API degrada com elegância sem ele.
@SpringBootTest(properties = "app.intelligence.base-url=http://localhost:1")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true) // sem Docker, os testes são PULADOS (aparecem como "Skipped"), não falham
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
}
