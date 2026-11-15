package com.edu.aitutor.framework.config;

import com.edu.aitutor.framework.web.WebSocketHandshakeInterceptor;
import com.edu.aitutor.framework.jwt.JwtUtil;
import com.edu.aitutor.module.chat.service.ChatWebSocketHandler;
import com.edu.aitutor.module.dashboard.websocket.DashboardWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final DashboardWebSocketHandler dashboardWebSocketHandler;
    private final JwtUtil jwtUtil;

    // Spring自动注入
    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           DashboardWebSocketHandler dashboardWebSocketHandler,
                           JwtUtil jwtUtil) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.dashboardWebSocketHandler = dashboardWebSocketHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                // new的时候把jwtUtil传进去！
                .addInterceptors(new WebSocketHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("*");
        // 教务数据大屏实时通道：同一套 JWT 握手校验，handler 内再校验 admin 角色
        registry.addHandler(dashboardWebSocketHandler, "/ws/dashboard")
                .addInterceptors(new WebSocketHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("*");
    }
}