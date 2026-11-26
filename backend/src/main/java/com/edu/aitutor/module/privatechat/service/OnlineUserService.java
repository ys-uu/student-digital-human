package com.edu.aitutor.module.privatechat.service;

import com.edu.aitutor.module.privatechat.repository.redis.OnlineStateRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私信在线状态管理。
 * 每个 WebSocket 连接对应一个独立 Redis key，并单独设置 TTL，避免一个活跃连接续期旧连接。
 */
@Service
public class OnlineUserService {

    private static final Logger log = LoggerFactory.getLogger(OnlineUserService.class);

    /** 单连接在线标记 TTL：心跳每 30 秒续期，60 秒容忍一次丢包。 */
    private static final Duration ONLINE_TTL = Duration.ofSeconds(60);

    private final OnlineStateRedisRepository onlineStateRedisRepository;

    /** 本实例持有的连接：userId → (sessionId → session)。 */
    private final Map<Long, Map<String, WebSocketSession>> localSessions = new ConcurrentHashMap<>();

    public OnlineUserService(OnlineStateRedisRepository onlineStateRedisRepository) {
        this.onlineStateRedisRepository = onlineStateRedisRepository;
    }

    public void online(Long userId, WebSocketSession session) {
        localSessions.computeIfAbsent(userId, key -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        try {
            onlineStateRedisRepository.saveSession(userId, session.getId(), ONLINE_TTL);
        } catch (Exception e) {
            log.warn("写入在线标记失败，userId={}：{}", userId, e.getMessage());
        }
    }

    public void offline(Long userId, WebSocketSession session) {
        Map<String, WebSocketSession> sessions = localSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                localSessions.remove(userId);
            }
        }
        try {
            onlineStateRedisRepository.deleteSession(userId, session.getId());
        } catch (Exception e) {
            log.warn("清理在线标记失败，userId={}：{}", userId, e.getMessage());
        }
    }

    public void touch(Long userId, WebSocketSession session) {
        try {
            onlineStateRedisRepository.saveSession(userId, session.getId(), ONLINE_TTL);
        } catch (Exception e) {
            log.debug("心跳续期失败，userId={}：{}", userId, e.getMessage());
        }
    }

    public boolean isOnline(Long userId) {
        try {
            if (onlineStateRedisRepository.existsAnySession(userId)) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Redis 在线状态查询失败，退化为本地判断：{}", e.getMessage());
        }

        Map<String, WebSocketSession> sessions = localSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** 定时关闭 Redis 中已经不存在的本地半开连接。 */
    @Scheduled(fixedDelay = 15000)
    public void evictStaleLocalSessions() {
        try {
            localSessions.forEach((userId, sessions) ->
                    sessions.entrySet().removeIf(entry -> {
                        boolean exists = onlineStateRedisRepository.hasSession(userId, entry.getKey());
                        if (!exists) {
                            closeQuietly(entry.getValue());
                            return true;
                        }
                        return false;
                    }));
            localSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        } catch (Exception e) {
            log.debug("本地连接清理跳过：{}", e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SESSION_NOT_RELIABLE);
            }
        } catch (Exception e) {
            log.debug("关闭半开连接失败，sessionId={}", session.getId());
        }
    }

    public Map<String, WebSocketSession> localSessionsOf(Long userId) {
        Map<String, WebSocketSession> sessions = localSessions.get(userId);
        return sessions == null ? Collections.emptyMap() : sessions;
    }

    public Set<Long> localOnlineUserIds() {
        return localSessions.keySet();
    }
}
