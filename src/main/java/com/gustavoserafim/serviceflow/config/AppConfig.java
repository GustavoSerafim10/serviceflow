package com.gustavoserafim.serviceflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling // habilita métodos @Scheduled (ex: limpeza diária de refresh tokens expirados)
@EnableConfigurationProperties(BusinessHoursProperties.class) // registra o record como bean, preenchido do application.yml
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
