package com.edu.aitutor.framework.security;

import com.edu.aitutor.common.RedisKeys;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.rbac.mapper.RbacMapper;
import com.edu.aitutor.module.user.mapper.UserMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户权限加载器：查询用户角色与权限码并转换为 GrantedAuthority，权限码集合优先读 Redis 缓存。
 */
@Slf4j
@Service
public class RbacUserDetailsService implements UserDetailsService {

    /** 权限缓存 TTL：24 小时兜底，角色/权限变更时由 clearPermCache 主动清除 */
    private static final Duration PERM_CACHE_TTL = Duration.ofHours(24);

    private static final TypeReference<List<String>> PERM_LIST_TYPE = new TypeReference<>() {
    };

    @Resource
    private UserMapper userMapper;
    @Resource
    private RbacMapper rbacMapper;
    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 标准契约：按账号（user_no）加载登录用户
     */
    @Override
    public LoginUser loadUserByUsername(String userNo) throws UsernameNotFoundException {
        User user = userMapper.selectByUserNo(userNo);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + userNo);
        }
        return buildLoginUser(user);
    }

    /**
     * 过滤器按 JWT 中的 userId 加载，避免重复按账号回查
     */
    public LoginUser loadUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在, id=" + userId);
        }
        return buildLoginUser(user);
    }

    /**
     * 按 userId 清除权限缓存，角色/权限变更后调用
     */
    public void clearPermCache(Long userId) {
        redisTemplate.delete(String.format(RedisKeys.USER_PERMS, userId));
    }

    /**
     * 获取用户权限码集合（优先读缓存），供 /auth/me 与登录返回等业务场景使用
     */
    public List<String> listPermCodes(Long userId) {
        return loadPermCodesWithCache(userId);
    }

    /**
     * 组装登录用户：角色加 ROLE_ 前缀，权限码原样保留
     */
    private LoginUser buildLoginUser(User user) {
        List<String> roleCodes = rbacMapper.selectRoleCodesByUserId(user.getId());
        List<String> permCodes = loadPermCodesWithCache(user.getId());
        List<GrantedAuthority> authorities = new ArrayList<>(roleCodes.size() + permCodes.size());
        for (String roleCode : roleCodes) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode.toUpperCase()));
        }
        for (String permCode : permCodes) {
            authorities.add(new SimpleGrantedAuthority(permCode));
        }
        return new LoginUser(user.getId(), user.getUserNo(), user.getPasswordHash(),
                user.getStatus(), authorities);
    }

    /**
     * 权限码优先读 Redis，未命中则查库并回写缓存
     */
    private List<String> loadPermCodesWithCache(Long userId) {
        String key = String.format(RedisKeys.USER_PERMS, userId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, PERM_LIST_TYPE);
            } catch (Exception e) {
                log.warn("权限缓存反序列化失败，回源查询 key={}", key);
            }
        }
        List<String> permCodes = rbacMapper.selectPermCodesByUserId(userId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(permCodes), PERM_CACHE_TTL);
        } catch (Exception e) {
            log.warn("权限缓存写入失败 key={} msg={}", key, e.getMessage());
        }
        return permCodes;
    }
}
