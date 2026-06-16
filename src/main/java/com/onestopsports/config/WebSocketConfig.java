package com.onestopsports.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

// WebSockets allow the server to PUSH data to the browser without the browser asking.
// This is used for live score updates — instead of the frontend polling every 30s,
// the server can instantly push a message the moment a score changes.
// We use STOMP, which is a simple messaging protocol that runs on top of WebSocket.
@Configuration
@EnableWebSocketMessageBroker // Enables Spring's WebSocket message handling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Spring Boot's auto-configured ObjectMapper — already has JavaTimeModule and all
    // other registered modules, so LocalDateTime fields serialise to ISO-8601 strings.
    private final ObjectMapper objectMapper;

    // Which website origins may open the live-scores WebSocket. Unlike the REST calls (which
    // Vercel proxies same-origin), the browser connects this WebSocket straight from the
    // Vercel site to the backend — a genuine cross-origin connection — so this list is what
    // actually gates it. Read from the same `app.cors.allowed-origin-patterns` property the
    // REST CORS config uses, so there's one place to manage allowed origins. ":*" is a
    // fallback for when the property is absent (allows any origin — only hit in tests).
    @Value("${app.cors.allowed-origin-patterns:*}")
    private List<String> allowedOriginPatterns;

    public WebSocketConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // "/topic" is where the server broadcasts messages to all subscribers.
        // e.g. the frontend subscribes to "/topic/matches/live" to receive live score updates.
        config.enableSimpleBroker("/topic");

        // "/app" is the prefix for messages sent FROM the client TO the server.
        // Not heavily used in this app since data flows mainly server → client.
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // "/ws" — plain WebSocket endpoint (no SockJS).
        // The frontend connects using @stomp/stompjs with a native ws:// URL,
        // which works in all modern browsers without any extra library.
        // SockJS was removed because its CJS module causes Vite to crash at import time.
        // Only allow the configured origins (dev localhost + the Vercel site) to connect,
        // instead of the old "*". setAllowedOriginPatterns takes varargs, so we hand it the
        // list as an array.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]));
    }

    // Override the STOMP message converter so it uses Spring Boot's ObjectMapper.
    // Without this override, the STOMP broker creates a fresh ObjectMapper without JavaTimeModule,
    // which crashes when it tries to serialise LocalDateTime fields in MatchDto.
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        // Set JSON as the default content type for STOMP messages so the frontend
        // receives "Content-Type: application/json" and can parse the body correctly.
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);

        // Build a STOMP-specific JSON converter backed by Spring Boot's ObjectMapper.
        // That ObjectMapper already has JavaTimeModule registered, so LocalDateTime → ISO string.
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setContentTypeResolver(resolver);
        messageConverters.add(converter);

        // Return false so Spring still adds its default String and byte[] converters on top.
        // Returning true would mean ONLY our converter is used, dropping string/binary support.
        return false;
    }
}
