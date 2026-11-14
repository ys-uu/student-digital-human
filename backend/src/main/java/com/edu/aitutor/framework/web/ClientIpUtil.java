package com.edu.aitutor.framework.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * 客户端真实 IP 解析。
 *
 * <p>只有请求直接来自内网 / 回环地址（典型场景是 Docker 网络中的 Nginx）时才信任
 * {@code X-Real-IP}、{@code X-Forwarded-For}；公网客户端直接访问后端时一律使用
 * {@code remoteAddr}，避免通过伪造请求头绕过 IP 限流。</p>
 *
 * <p>Nginx 已用 {@code $remote_addr} 覆盖写入 {@code X-Real-IP}，因此内网链路优先取该头，
 * 其次取 XFF 的第一段，最后回退到 {@code remoteAddr}。只接受形如 IPv4 / IPv6 的值，
 * 避免把任意头内容写进 Redis key。</p>
 */
public final class ClientIpUtil {

    private static final Pattern IP_PATTERN = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

    private ClientIpUtil() {
    }

    public static String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isPrivateOrLoopback(remote)) {
            // 公网直连：请求头不可信，只认 TCP 对端地址
            String normalized = valid(remote);
            return normalized == null ? "unknown" : normalized;
        }

        String realIp = valid(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            for (String part : forwarded.split(",")) {
                String candidate = valid(part);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        String normalized = valid(remote);
        return normalized == null ? "unknown" : normalized;
    }

    private static boolean isPrivateOrLoopback(String ip) {
        if (ip == null || ip.isBlank() || !IP_PATTERN.matcher(ip.trim()).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip.trim());
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static String valid(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || !IP_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }
}