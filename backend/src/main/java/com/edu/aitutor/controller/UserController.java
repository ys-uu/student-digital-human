package com.edu.aitutor.controller;

import com.edu.aitutor.vo.LoginVO;
import com.edu.aitutor.entity.User;
import com.edu.aitutor.service.UserService;
import com.edu.aitutor.util.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user") // ✅ 修改1：加上api/v1版本前缀
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Object login(@RequestBody LoginVO loginVO){
        String username = loginVO.getUsername();
        String password = loginVO.getPassword();
        System.out.println("收到登录请求 username="+username);
        User user = userService.login(username,password);
        if(user == null){
            return Map.of("code",400,"msg","账号或密码错误");
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        Map<String,Object> data = new HashMap<>();
        data.put("token",token);
        data.put("userId",user.getId());   // ✅ 修改2：id → userId
        data.put("role", user.getRole());
        data.put("realName", user.getRealName()); // ✅ 修改3：新增realName
        return Map.of("code",200,"msg","登录成功","data",data);
    }
}

