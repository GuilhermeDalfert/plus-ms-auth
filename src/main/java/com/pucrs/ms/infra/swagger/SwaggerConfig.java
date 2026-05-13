package com.pucrs.ms.infra.swagger;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {

        Info info = new Info()
                .title("Microsserviço de Autenticação")
                .version("v1")
                .description("API responsável pela autenticação e gerenciamento de usuários");

        return new OpenAPI()
                .info(info);
    }

}
