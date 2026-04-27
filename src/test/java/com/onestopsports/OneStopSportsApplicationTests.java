package com.onestopsports;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

// Smoke test — verifies the full Spring application context loads without errors.
// Uses the "test" profile (H2 in-memory database, no Flyway migrations).
//
// RedisConnectionFactory is mocked so this test doesn't require a running Redis instance.
// Our custom RedisConfig bean needs RedisConnectionFactory to create the RedisCacheManager —
// by mocking it here, the bean can be constructed and the context loads cleanly.
@SpringBootTest
@ActiveProfiles("test")
class OneStopSportsApplicationTests {

    // Provides a mock RedisConnectionFactory so RedisConfig can build the CacheManager bean
    // without a real Redis server. No actual Redis connections are made during context startup.
    @MockBean
    RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
        // If this test passes, the entire Spring context wired up correctly.
        // All beans resolved, no missing dependencies, no circular references.
    }
}
