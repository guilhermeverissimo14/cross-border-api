package com.guilherme.crossborderapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI crossBorderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cross Border API")
                        .description("Serviço de remessa entre usuários PF/PJ com conversão de Real para Dólar, "
                                + "usando a cotação oficial do Banco Central do Brasil.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Guilherme")
                                .email("guilherme.andrade@siprov.com.br")));
    }
}
