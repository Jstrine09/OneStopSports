package com.onestopsports.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        // Build a custom ObjectMapper for Redis serialization.
        // The no-arg GenericJackson2JsonRedisSerializer creates a bare ObjectMapper
        // that can't handle java.time.LocalDateTime — we need to register JavaTimeModule.
        //
        // We also replicate the type-info configuration from the default constructor so that
        // Jackson embeds an "@class" field in the JSON, which lets it deserialise back to
        // the correct Java type without us having to specify it at read time.
        ObjectMapper redisMapper = new ObjectMapper();

        // JavaTimeModule teaches Jackson how to read/write LocalDate, LocalDateTime, etc.
        // WRITE_DATES_AS_TIMESTAMPS = false → ISO-8601 strings ("2025-04-26T00:00:00")
        // rather than epoch millisecond numbers (which are harder to read in Redis).
        redisMapper.registerModule(new JavaTimeModule());
        redisMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Default typing: embed "@class" in every serialised object so Jackson knows the
        // concrete type to instantiate on read (e.g. ArrayList, MatchDto, TeamDto).
        // BasicPolymorphicTypeValidator.allowIfSubType(Object.class) permits any type —
        // safe here because only our own code writes to this Redis instance.
        redisMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(Object.class) // Allow any class in our own cache
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30)) // Cached data expires after 30 seconds
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                // Pass our custom mapper so LocalDateTime serialises correctly
                                new GenericJackson2JsonRedisSerializer(redisMapper)));

        // Build the cache manager using our custom config as the default for all caches
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
