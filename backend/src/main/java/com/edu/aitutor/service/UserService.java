package com.edu.aitutor.service;

import com.edu.aitutor.dto.UserLoginDTO;
import com.edu.aitutor.entity.User;

public interface UserService {
    User login(UserLoginDTO userLoginDTO);

    void updateLoginInfo(Long userId, String clientIp);
}
