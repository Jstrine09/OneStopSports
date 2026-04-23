package com.onestopsports.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

// Redis is a fast in-memory store we use to cache live match data.
// Instead of calling the football-data.org API on every request,
// we store the result in Redis for 30 seconds so repeated requests are instant.
@Configuration
@EnableCaching // Activates Spring's @Cacheable annotation support
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30)) // Cached data expires after 30 seconds
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                // Store values as JSON (with type info), so they deserialise correctly
                                new GenericJackson2JsonRedisSerializer()));

        // Build the cache manager using our custom config as the default for all caches
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
