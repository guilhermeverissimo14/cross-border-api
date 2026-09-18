package com.guilherme.crossborderapi.config;

import jakarta.annotation.PreDestroy;
import org.h2.tools.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
