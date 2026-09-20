package com.edu.aitutor.config;

import com.edu.aitutor.interceptor.WebSocketHandshakeInterceptor;
import com.edu.aitutor.util.JwtUtil;
import com.edu.aitutor.ws.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtUtil jwtUtil;

    // Spring自动注入
    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler, JwtUtil jwtUtil) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                // new的时候把jwtUtil传进去！
                .addInterceptors(new WebSocketHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("*");
    }
}