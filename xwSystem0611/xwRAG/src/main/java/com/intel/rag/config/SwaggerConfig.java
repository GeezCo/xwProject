package com.intel.rag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RAG知识库系统API")
                        .version("1.0.0")
                        .description("基于Elasticsearch和BGE的RAG检索系统API文档")
                        .contact(new Contact()
                                .name("RAG Team")
                                .email("support@example.com")));
    }
}
