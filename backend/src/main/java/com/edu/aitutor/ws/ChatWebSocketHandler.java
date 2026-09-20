package com.edu.aitutor.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    // userId -> session 在线用户存储
    public static ConcurrentHashMap<Long, WebSocketSession> onlineSessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从握手拦截器存入的attributes拿到userId
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        onlineSessionMap.put(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 接收前端ws消息
        String payload = message.getPayload();
        System.out.println("收到前端消息：" + payload);

        // 示例：给当前连接的用户回消息（后面对接AI数字人在这里写业务）
        session.sendMessage(new TextMessage("收到你的消息：" + payload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        onlineSessionMap.remove(userId);
    }
}
