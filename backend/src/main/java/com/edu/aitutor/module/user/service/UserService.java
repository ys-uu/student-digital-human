package com.edu.aitutor.module.user.service;

import com.edu.aitutor.module.user.dto.UserLoginDTO;
import com.edu.aitutor.module.user.entity.User;

public interface UserService {
    User login(UserLoginDTO userLoginDTO);

    void updateLoginInfo(Long userId, String clientIp);
}
