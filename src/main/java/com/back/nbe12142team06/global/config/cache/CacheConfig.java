package com.back.nbe12142team06.global.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(){

        CaffeineCacheManager manager = new CaffeineCacheManager("postList");
        manager.setCaffeine(
                            Caffeine.newBuilder()
                            .expireAfterWrite(Duration.ofSeconds(30))
                            .maximumSize(500));
        return manager;
    }
}
