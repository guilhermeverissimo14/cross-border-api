package com.guilherme.crossborderapi.config;

import jakarta.annotation.PreDestroy;
import org.h2.tools.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Desligado nos testes (ver src/test/resources/application.properties) pra não disputar a porta 9092
@Configuration
@ConditionalOnProperty(prefix = "app.h2-tcp-server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class H2TcpServerConfig {

    private Server tcpServer;

    @Bean
    public Server h2TcpServer() throws java.sql.SQLException {
        tcpServer = Server.createTcpServer("-tcpAllowOthers", "-tcpPort", "9092").start();
        return tcpServer;
    }

    @PreDestroy
    public void stopServer() {
        if (tcpServer != null) {
            tcpServer.stop();
        }
    }
}
