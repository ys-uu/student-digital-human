package com.edu.aitutor.framework.security;

import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.rbac.mapper.RbacMapper;
import com.edu.aitutor.module.rbac.repository.PermissionCacheRedisRepository;
import com.edu.aitutor.module.user.mapper.UserMapper;
import jakarta.annotation.Resource;
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
@Service
public class RbacUserDetailsService implements UserDetailsService {

    /** 权限缓存 TTL：24 小时兜底，角色/权限变更时由 clearPermCache 主动清除 */
    private static final Duration PERM_CACHE_TTL = Duration.ofHours(24);

    @Resource
    private UserMapper userMapper;
    @Resource
    private RbacMapper rbacMapper;
    @Resource
    private PermissionCacheRedisRepository permissionCacheRedisRepository;

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
        permissionCacheRedisRepository.evict(userId);
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
        List<String> cached = permissionCacheRedisRepository.find(userId);
        if (cached != null) {
            return cached;
        }
        List<String> permCodes = rbacMapper.selectPermCodesByUserId(userId);
        permissionCacheRedisRepository.save(userId, permCodes, PERM_CACHE_TTL);
        return permCodes;
    }
}
