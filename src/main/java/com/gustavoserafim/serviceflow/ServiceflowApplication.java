package com.gustavoserafim.serviceflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe de entrada da aplicação.
 *
 * @SpringBootApplication é uma anotação "combo" que junta três outras:
 *  - @Configuration: esta classe pode declarar beans (objetos gerenciados pelo Spring)
 *  - @EnableAutoConfiguration: o Spring Boot olha o que está no classpath (ex: driver
 *    do Postgres + Data JPA) e configura automaticamente um DataSource, um
 *    EntityManager, etc. Isso é a "mágica" do Spring Boot: menos configuração manual.
 *  - @ComponentScan: o Spring varre este pacote (com.gustavoserafim.serviceflow) e
 *    todos os sub-pacotes procurando classes anotadas (@Service, @RestController,
 *    @Repository...) para registrar como beans.
 *
 * O método main() é um main Java comum. SpringApplication.run() é quem de fato:
 *  1. Cria o "contexto" do Spring (o container que guarda todos os beans);
 *  2. Sobe o servidor web embutido (Tomcat, por causa do spring-boot-starter-web);
 *  3. Aciona a auto-configuração (ex: conectar no Postgres usando o que está em
 *     application.yml, e rodar as migrations do Flyway antes de liberar a aplicação).
 *
 * Nesta Etapa 1 ainda não existem controllers nem entidades — o objetivo aqui é
 * só provar que a aplicação sobe e consegue falar com o banco.
 */
@SpringBootApplication
public class ServiceflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceflowApplication.class, args);
    }

}
