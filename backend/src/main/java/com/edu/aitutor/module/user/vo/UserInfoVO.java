package com.edu.aitutor.module.user.vo;

import com.edu.aitutor.module.user.entity.User;
import lombok.Data;

import java.util.List;

/**
 * 用户信息视图对象（接口文档 3.1 / 3.4 的 user 结构）。
 */
@Data
public class UserInfoVO {

    private Long id;

    /** 登录账号 user_no */
    private String username;

    /** 昵称，当前系统以真实姓名承载 */
    private String nickname;

    private String realName;

    private String avatarUrl;

    /** 角色数字：1 学生，2 教师，3 教务 */
    private Integer role;

    /** 角色字符串，前端用于跳转与权限判断 */
    private String roleName;

    /** 当前用户拥有的权限码集合，供前端按钮级控制（v-permission）与路由守卫使用 */
    private List<String> permissions;

    private String phone;

    private String email;

    public static UserInfoVO from(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUserNo());
        vo.setNickname(user.getRealName());
        vo.setRealName(user.getRealName());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRole(user.getRole());
        vo.setRoleName(toRoleName(user.getRole()));
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        return vo;
    }

    public static String toRoleName(Integer role) {
        if (role == null) {
            return "student";
        }
        return switch (role) {
            case 2 -> "teacher";
            case 3 -> "admin";
            default -> "student";
        };
    }
}
