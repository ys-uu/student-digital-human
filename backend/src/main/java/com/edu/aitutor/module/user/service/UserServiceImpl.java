package com.edu.aitutor.module.user.service;

import com.edu.aitutor.module.user.dto.UserLoginDTO;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.time.LocalDateTime;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录校验，入参改为DTO
     */
    @Override
    public User login(UserLoginDTO userLoginDTO) {
        String username = userLoginDTO.getUsername();
        String password = userLoginDTO.getPassword();

        // 1. 根据账号查询用户（对应数据库user_no）
        User user = userMapper.selectByUserNo(username);
        log.info("数据库查到user：{}", user);
        if(user == null){
            log.warn("账号不存在:{}", username);
            return null;
        }
        // ✅ 新增：校验账号是否被禁用
        if(user.getStatus() == 0){
            log.warn("账号{}已被禁用", username);
            return null;
        }

        log.info("前端传的明文密码：{}", password);
        log.info("数据库取出加密密码：{}", user.getPasswordHash());
        boolean check = passwordEncoder.matches(password, user.getPasswordHash());
        log.info("密码校验结果：{}", check);

        if(!check){
            log.warn("账号{}密码校验失败", username);
            return null;
        }
        return user;
    }

    /**
     * 更新本次登录IP和登录时间
     */
    @Override
    public void updateLoginInfo(Long userId, String clientIp) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setLastLoginIp(clientIp);
        updateUser.setLastLoginAt(LocalDateTime.now());
        userMapper.updateLoginInfo(updateUser);
    }
}

