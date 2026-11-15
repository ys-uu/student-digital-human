package com.edu.aitutor.module.user.controller;

import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.user.dto.UserLoginDTO;
import com.edu.aitutor.module.user.vo.LoginVO;
import com.edu.aitutor.module.user.entity.User;
import com.edu.aitutor.module.user.service.UserService;
import com.edu.aitutor.framework.jwt.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/user") // ✅ 修改1：加上api/v1版本前缀
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String,Object>> login(@Valid @RequestBody LoginVO loginVO){
        log.info("收到登录请求 username={}", loginVO.getUsername());

        // ========== 新增：VO → DTO 转换 ==========
        UserLoginDTO userLoginDTO = new UserLoginDTO();
        userLoginDTO.setUsername(loginVO.getUsername());
        userLoginDTO.setPassword(loginVO.getPassword());

        // 传给Service层，Service入参改成 UserLoginDTO
        User user = userService.login(userLoginDTO);
        if(user == null){
            return Result.error(400,"账号或密码错误");
        }
// 传入 userNo，角色字符串，userId
        String token = jwtUtil.generateToken(user.getUserNo(), user.getRole().toString(), user.getId());

        Map<String,Object> data = new HashMap<>();
        data.put("token",token);
        data.put("userId",user.getId());
        data.put("role",user.getRole());
        data.put("realName",user.getRealName());
        return Result.success(data);

    }
}

