package com.edu.aitutor.framework.config;

import com.edu.aitutor.framework.web.WebSocketHandshakeInterceptor;
import com.edu.aitutor.framework.jwt.JwtUtil;
import com.edu.aitutor.module.dashboard.websocket.DashboardWebSocketHandler;
import com.edu.aitutor.module.privatechat.websocket.PrivateWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 通道注册。
 *
 * ============================================================================
 * 【Q1 实时性】服务端如何主动推送消息给客户端
 * ----------------------------------------------------------------------------
 * PPT P03 原问题：HTTP 是一问一答，服务端无法主动找客户端；
 *               必须建立一条服务端可以主动推数据的通道。
 * 本类职责：把 PrivateWebSocketHandler 挂到 /ws/private，握手时用 JWT 鉴权。
 * 关键行：见下方 registerWebSocketHandlers 中 "/ws/private" 的注册。
 * 触发链路：客户端 new WebSocket('/ws/private?token=xxx')
 *          → HTTP 带 Upgrade:websocket 头
 *          → 服务端回 101 Switching Protocols
 *          → TCP 连接脱离 HTTP，双方随时互发帧（毫秒级）
 * ============================================================================
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PrivateWebSocketHandler privateWebSocketHandler;
    private final DashboardWebSocketHandler dashboardWebSocketHandler;
    private final JwtUtil jwtUtil;

    // Spring自动注入
    public WebSocketConfig(PrivateWebSocketHandler privateWebSocketHandler,
                           DashboardWebSocketHandler dashboardWebSocketHandler,
                           JwtUtil jwtUtil) {
        this.privateWebSocketHandler = privateWebSocketHandler;
        this.dashboardWebSocketHandler = dashboardWebSocketHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // ★★★【Q1 实时性】核心注册行 ★★★
        // 把私信处理器挂到 /ws/private，这是"服务端主动推"的物理通道：
        //   · addInterceptors：握手阶段校验 JWT（PPT P16「握手即鉴权」），
        //     失败直接拒绝升级，防止匿名连接白嫖通道；
        //   · setAllowedOrigins("*")：开发期放开跨域，生产应收紧为具体域名。
        //
        // 与 PPT P05 选型结论对应：
        //   轮询 → 延迟 = 间隔，大量空请求
        //   SSE  → 单向，只能服务端→客户端，发消息仍需另开 HTTP
        //   WebSocket → ★ 双向全双工，毫秒级，连接只建一次（本项目采用）
        //
        // 说明：原 /ws/chat 回显骨架已移除——它无业务逻辑，
        //       且与接口文档 17.5.1「AI 对话走 SSE」的设计冲突；
        //       AI 数字人流式对话另行排期，不在本期范围。
        registry.addHandler(privateWebSocketHandler, "/ws/private")
                .addInterceptors(new WebSocketHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("*");
        // 教务数据大屏实时通道：同一套 JWT 握手校验，handler 内再校验 admin 角色
        registry.addHandler(dashboardWebSocketHandler, "/ws/dashboard")
                .addInterceptors(new WebSocketHandshakeInterceptor(jwtUtil))
                .setAllowedOrigins("*");
    }
}