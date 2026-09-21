package com.gustavoserafim.serviceflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI. O springdoc lê os controllers e gera
 * o contrato da API sozinho; aqui só acrescentamos o título e o "cadeado":
 * declaramos que a API usa JWT (Bearer) para que o botão "Authorize" do
 * Swagger UI envie o header Authorization nas chamadas de teste.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI serviceFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ServiceFlow API")
                        .version("1.0")
                        .description("Sistema de Gestão de Chamados de TI (ITSM): chamados, SLA, "
                                + "usuários e autenticação JWT. Faça login em POST /api/auth/login, "
                                + "copie o token e clique em \"Authorize\"."))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Aplica o esquema a todas as rotas (o login continua funcionando sem token).
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
