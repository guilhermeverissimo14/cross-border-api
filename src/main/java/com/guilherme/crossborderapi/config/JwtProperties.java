package com.guilherme.crossborderapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        // HS256 exige pelo menos 32 bytes
        @NotBlank @Size(min = 32) String secret,
        @DefaultValue("1h") Duration expiration
) {
}
