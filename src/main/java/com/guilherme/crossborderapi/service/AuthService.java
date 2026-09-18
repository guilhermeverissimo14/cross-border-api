package com.guilherme.crossborderapi.service;

import com.guilherme.crossborderapi.config.JwtProperties;
import com.guilherme.crossborderapi.dto.auth.LoginRequest;
import com.guilherme.crossborderapi.dto.auth.LoginResponse;
import com.guilherme.crossborderapi.entity.User;
import com.guilherme.crossborderapi.exception.InvalidCredentialsException;
import com.guilherme.crossborderapi.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Mesmo erro para e-mail desconhecido e senha errada, para não revelar quais e-mails existem
        User user = userRepository.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.expiration()))
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        log.info("Login realizado: userId={}", user.getId());
        return new LoginResponse(token, TOKEN_TYPE, jwtProperties.expiration().toSeconds());
    }
}
