package com.edu.aitutor.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 登录用户主体：承载用户标识、账号状态与权限集合，作为 SecurityContext 中的 principal。
 */
public class LoginUser implements UserDetails {

    private final Long userId;

    private final String username;

    @JsonIgnore
    private final String password;

    private final Integer status;

    private final Collection<? extends GrantedAuthority> authorities;

    public LoginUser(Long userId, String username, String password, Integer status,
                     Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.status = status;
        this.authorities = authorities;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @JsonIgnore
    @Override
    public boolean isEnabled() {
        return status == null || status == 1;
    }
}
