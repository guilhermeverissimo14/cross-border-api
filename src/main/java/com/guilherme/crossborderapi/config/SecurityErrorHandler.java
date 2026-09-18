package com.guilherme.crossborderapi.config;

import com.guilherme.crossborderapi.exception.handler.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// O filtro de segurança roda antes dos controllers, então o GlobalExceptionHandler não o alcança:
// os 401/403 daqui são escritos no mesmo formato de erro do resto da API.
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) {
        log.warn("Requisição sem autenticação válida: {} {}", request.getMethod(), request.getRequestURI());
        write(response, HttpStatus.UNAUTHORIZED,
                "Autenticação necessária: envie o header Authorization: Bearer <token>");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
        log.warn("Acesso negado: {} {}", request.getMethod(), request.getRequestURI());
        write(response, HttpStatus.FORBIDDEN, "Acesso negado");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, message));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
