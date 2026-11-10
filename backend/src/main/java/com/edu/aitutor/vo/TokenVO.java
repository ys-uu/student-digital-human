package com.edu.aitutor.vo;

import lombok.Data;

/**
 * 登录成功的令牌响应（接口文档 3.1）。
 */
@Data
public class TokenVO {

    private String accessToken;

    private String refreshToken;

    /** access token 有效期（秒） */
    private Long expiresIn;

    private String tokenType;

    private UserInfoVO user;
}
