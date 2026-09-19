package com.guilherme.crossborderapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EXCHANGE_RATES = "exchangeRates";

    // Só entram cotações do dia exato, que não mudam depois de publicadas: o TTL serve para limitar a memória.
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(EXCHANGE_RATES);
        cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofDays(1)));
        return cacheManager;
    }
}
