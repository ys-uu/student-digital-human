package com.edu.aitutor.service;

import com.edu.aitutor.entity.User;

public interface UserService {
    User login(String username,String password);
}
