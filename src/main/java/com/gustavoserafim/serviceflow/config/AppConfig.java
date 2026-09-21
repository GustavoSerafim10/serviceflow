package com.gustavoserafim.serviceflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /**
     * Relógio injetável. Em vez de chamar Instant.now() direto nas regras de
     * negócio, o service pede "que horas são?" ao Clock. Em produção é o
     * relógio do sistema (UTC); nos testes injetamos um Clock.fixed(...) e
     * controlamos o tempo — essencial para testar prazos de SLA.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
