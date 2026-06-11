package com.qy.dch.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class EmbeddingConfig {

    @Bean
    public RestTemplate embeddingRestTemplate() {
        return new RestTemplate();
    }
}
