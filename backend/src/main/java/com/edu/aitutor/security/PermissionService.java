package com.edu.aitutor.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 权限校验 SpEL Bean，统一以 @PreAuthorize("@ss.hasPerm('homework:create')") 形式调用。
 */
@Component("ss")
public class PermissionService {

    /** 通配权限码：命中即拥有全部权限，新增接口默认放行，无需逐个授权 */
    public static final String WILDCARD = "*:*";

    /**
     * 持有通配码直接返回 true，否则按权限码精确匹配
     */
    public boolean hasPerm(String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (WILDCARD.equals(value) || code.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 任一权限码命中即放行
     */
    public boolean hasAnyPerm(String... codes) {
        if (codes == null || codes.length == 0) {
            return false;
        }
        for (String code : codes) {
            if (hasPerm(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 角色校验：入参不带 ROLE_ 前缀，如 hasRole("teacher")
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String target = "ROLE_" + role.toUpperCase();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (target.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
