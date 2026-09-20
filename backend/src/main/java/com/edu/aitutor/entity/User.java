package com.edu.aitutor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String realName;   // 新增，对应数据库 real_name
    private String role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime; // 新增，对应数据库 update_time
}
