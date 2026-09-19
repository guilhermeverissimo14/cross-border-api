package com.guilherme.crossborderapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guilherme.crossborderapi.config.JwtProperties;
import com.guilherme.crossborderapi.dto.auth.LoginRequest;
import com.guilherme.crossborderapi.dto.auth.LoginResponse;
import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.exception.InvalidCredentialsException;
import com.guilherme.crossborderapi.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T15:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtEncoder,
                new JwtProperties("x".repeat(32), Duration.ofMinutes(30)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void tokenIsIssuedAtTheClockInstantAndExpiresAfterTheConfiguredDuration() {
        when(userRepository.findByEmail("ana@x.com")).thenReturn(
                Optional.of(User.builder().id(7L).email("ana@x.com").password("hash").build()));
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(
                Jwt.withTokenValue("token").header("alg", "HS256").subject("7").build());

        LoginResponse response = authService.login(new LoginRequest("  ANA@x.com ", "senha123"));

        ArgumentCaptor<JwtEncoderParameters> params = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(params.capture());
        assertThat(params.getValue().getClaims().getSubject()).isEqualTo("7");
        assertThat(params.getValue().getClaims().getIssuedAt()).isEqualTo(NOW);
        assertThat(params.getValue().getClaims().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.expiresIn()).isEqualTo(1800);
    }

    @Test
    void rejectsAWrongPasswordWithTheSameErrorAsAnUnknownEmail() {
        when(userRepository.findByEmail("ana@x.com")).thenReturn(
                Optional.of(User.builder().id(7L).password("hash").build()));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);
        when(userRepository.findByEmail("outro@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@x.com", "errada")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Credenciais inválidas");
        assertThatThrownBy(() -> authService.login(new LoginRequest("outro@x.com", "qualquer")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Credenciais inválidas");
    }
}
