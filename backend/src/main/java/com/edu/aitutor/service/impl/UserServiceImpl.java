package com.edu.aitutor.service.impl;

import com.edu.aitutor.entity.User;
import com.edu.aitutor.mapper.UserMapper;
import com.edu.aitutor.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

@Service
public class UserServiceImpl implements UserService {
    @Resource
    private UserMapper userMapper;

    // Spring自带加密编码器
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User login(String username, String password) {
        User user = userMapper.selectByUsername(username);
        System.out.println("数据库查到user：" + user);
        if(user == null){
            System.out.println("账号不存在");
            return null;
        }
        System.out.println("前端传的明文密码：" + password);
        System.out.println("数据库取出加密密码：" + user.getPassword());
        boolean check = passwordEncoder.matches(password, user.getPassword());
        System.out.println("密码校验结果：" + check);
        if(!check){
            return null;
        }
        return user;
    }

}
