package com.edu.aitutor.common;

/**
 * 业务错误码常量，编号对齐接口文档 2.6 节；
 * 1011、1012 为文档外补充的短信验证码与账号禁用场景。
 * 成功沿用项目既有约定 200（HTTP 风格，与文档 3.8 扫码章节一致）。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final int SUCCESS = 200;

    public static final int PARAM_ERROR = 1001;
    public static final int TOKEN_INVALID = 1002;
    public static final int TOKEN_EXPIRED = 1003;
    public static final int NO_PERMISSION = 1004;
    public static final int NOT_FOUND = 1005;
    public static final int TOO_FREQUENT = 1006;

    /** 二维码已过期或不存在（文档 2.6 已定义） */
    public static final int QR_EXPIRED = 1010;
    /** 短信验证码错误或已过期（补充） */
    public static final int SMS_CODE_ERROR = 1011;
    /** 账号已被禁用（补充） */
    public static final int ACCOUNT_DISABLED = 1012;
    /** 触发反爬风控（可疑 UA、蜜罐路径、IP 封禁），HTTP 403（补充） */
    public static final int ACCESS_BLOCKED = 1013;

    public static final int SYSTEM_ERROR = 9999;
}
