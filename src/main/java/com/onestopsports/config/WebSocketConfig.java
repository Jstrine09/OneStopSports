package com.onestopsports.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// WebSockets allow the server to PUSH data to the browser without the browser asking.
// This is used for live score updates — instead of the frontend polling every 30s,
// the server can instantly push a message the moment a score changes.
// We use STOMP, which is a simple messaging protocol that runs on top of WebSocket.
@Configuration
@EnableWebSocketMessageBroker // Enables Spring's WebSocket message handling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

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
        // "/ws" is the URL the frontend connects to when opening a WebSocket.
        // SockJS is a fallback library — if the browser doesn't support WebSocket natively,
        // SockJS emulates it using long-polling or other techniques.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Allow connections from any origin (fine for dev)
                .withSockJS();
    }
}
