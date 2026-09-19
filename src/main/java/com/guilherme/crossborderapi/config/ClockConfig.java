package com.guilherme.crossborderapi.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // Fuso da JVM (no container, TZ=America/Sao_Paulo): é o mesmo que o Hibernate usa em created_at,que o limite diário compara com o "dia de hoje".
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
