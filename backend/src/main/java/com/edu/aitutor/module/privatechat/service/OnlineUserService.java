package com.edu.aitutor.module.privatechat.service;

import com.edu.aitutor.common.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私信在线状态管理。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li><b>不用进程内 static Map</b>：旧实现 onlineSessionMap 是 static ConcurrentHashMap，
 *       重启丢状态、多实例互不可见、一人只能存一个连接。架构设计文档 §9.2 第 3 条
 *       明确禁止运行时状态放在进程内，因此在线标记落 Redis，本地只保留 session 引用。</li>
 *   <li><b>按 userId 存集合</b>：同一账号多标签页/多设备各自保有连接，不再互相顶掉。</li>
 *   <li><b>Redis 标记带 TTL</b>：进程崩溃来不及清理时由 TTL 自然过期，
 *       避免出现"显示在线但推不到"的假在线。</li>
 *   <li><b>isOnline 查 Redis</b>：跨实例判断准确；推给本机只遍历本地集合，不碰网络。</li>
 * </ul>
 */
@Service
public class OnlineUserService {

    private static final Logger log = LoggerFactory.getLogger(OnlineUserService.class);

    /** 在线标记 TTL：心跳每 30 秒续期一次，60 秒容忍一次丢包 */
    private static final Duration ONLINE_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    /**
     * ★【Q4 状态与多端】本实例持有的连接：userId → (sessionId → session)。
     * <p>
     * 这里是实现"同一账号多设备同时在线"的关键：
     * 一个 userId 对应一个 Map，Map 里可以有多个 session（手机 + 电脑 + 多个标签页）。
     * 推送时遍历这个 Map 全部发送，各端都能立刻收到。
     * <p>
     * 为什么用 Map 而不是单个 session（旧实现的写法）：
     * 单个 session 会导致后登录的设备"顶掉"先登录的，先开的那端再也收不到消息。
     * <p>
     * 注意：这里只存<b>本进程可达</b>的 session 对象（WebSocketSession 无法跨进程传递），
     * "用户是否在线"这个全局判断走 Redis（见 {@link #isOnline}），两者分工明确。
     */
    private final Map<Long, Map<String, WebSocketSession>> localSessions = new ConcurrentHashMap<>();

    public OnlineUserService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * ★【Q4 + PPT P13】用户上线：登记本地 session 并写入 Redis 在线集合。
     * <p>
     * 两件事同时做：
     * <ol>
     *   <li><b>localSessions</b>：本进程真实可推的连接（Q4 多端并存）；</li>
     *   <li><b>Redis Set</b>：跨实例判断在线状态（P13 在线表 + P17 跨网关路由）。</li>
     * </ol>
     * <p>
     * TTL 60 秒是防"假在线"的关键：进程被 kill 来不及走 offline 时，
     * Redis 会在 60s 后自动清除该标记，避免出现"显示在线但推不到"。
     * 心跳每 30s 续期一次，所以正常在线不会过期。
     */
    public void online(Long userId, WebSocketSession session) {
        // 多端并存：同一 userId 下的 session 追加而非覆盖
        localSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        try {
            String key = String.format(RedisKeys.WS_ONLINE, userId);
            redisTemplate.opsForSet().add(key, session.getId());
            // TTL 兜底：进程异常退出时由过期机制自动清理，防止僵尸在线状态（PPT P13）
            redisTemplate.expire(key, ONLINE_TTL);
        } catch (Exception e) {
            // Redis 不可用时不阻断连接：本机推送仍可用，仅跨实例能力降级
            log.warn("写入在线标记失败，userId={}：{}", userId, e.getMessage());
        }
    }

    /**
     * 用户下线：移除本地 session；该用户已无任何连接时删除 Redis 标记。
     */
    public void offline(Long userId, WebSocketSession session) {
        Map<String, WebSocketSession> sessions = localSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                localSessions.remove(userId);
            }
        }
        try {
            String key = String.format(RedisKeys.WS_ONLINE, userId);
            redisTemplate.opsForSet().remove(key, session.getId());
            Long remaining = redisTemplate.opsForSet().size(key);
            if (remaining == null || remaining <= 0) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("清理在线标记失败，userId={}：{}", userId, e.getMessage());
        }
    }

    /**
     * 心跳续期：重置该用户在线标记的 TTL。
     */
    public void touch(Long userId, WebSocketSession session) {
        try {
            String key = String.format(RedisKeys.WS_ONLINE, userId);
            redisTemplate.opsForSet().add(key, session.getId());
            redisTemplate.expire(key, ONLINE_TTL);
        } catch (Exception e) {
            log.debug("心跳续期失败，userId={}：{}", userId, e.getMessage());
        }
    }

    /**
     * 判断用户是否在线（查 Redis，跨实例准确）。
     */
    public boolean isOnline(Long userId) {
        try {
            Long size = redisTemplate.opsForSet().size(String.format(RedisKeys.WS_ONLINE, userId));
            return size != null && size > 0;
        } catch (Exception e) {
            // Redis 异常时退化为本地判断，宁可漏推由客户端补拉，也不误判在线
            Map<String, WebSocketSession> sessions = localSessions.get(userId);
            return sessions != null && !sessions.isEmpty();
        }
    }

    /**
     * 取本实例持有的该用户全部连接，供推送使用。
     */
    public Map<String, WebSocketSession> localSessionsOf(Long userId) {
        Map<String, WebSocketSession> sessions = localSessions.get(userId);
        return sessions == null ? Collections.emptyMap() : sessions;
    }

    /**
     * 本实例当前持有的在线用户集合（仅用于日志与诊断）。
     */
    public Set<Long> localOnlineUserIds() {
        return localSessions.keySet();
    }
}