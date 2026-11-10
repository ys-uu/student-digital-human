package com.edu.aitutor.vo;

import lombok.Data;

/**
 * 模拟手机端候选账号（接口文档 3.9 mock-users，仅 dev 环境）。
 */
@Data
public class QrMockUserVO {

    private String account;

    private String realName;

    private Integer role;

    private Boolean inWhitelist;
}
