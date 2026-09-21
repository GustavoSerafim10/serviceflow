package com.gustavoserafim.serviceflow.config;

import com.gustavoserafim.serviceflow.integration.IntelligenceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Monta o RestClient dedicado ao serviço de inteligência: URL base e
 * timeouts vêm da configuração. O RestClient.Builder injetado já traz os
 * conversores JSON do Spring Boot (mesmo Jackson do resto da API).
 */
@Configuration
@EnableConfigurationProperties(IntelligenceProperties.class)
public class IntelligenceConfig {

    @Bean("intelligenceRestClient")
    public RestClient intelligenceRestClient(RestClient.Builder builder, IntelligenceProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
