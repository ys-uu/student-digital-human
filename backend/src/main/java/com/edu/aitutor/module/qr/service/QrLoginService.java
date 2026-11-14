package com.edu.aitutor.module.qr.service;

import com.edu.aitutor.module.qr.dto.QrScanDTO;
import com.edu.aitutor.module.qr.vo.QrCreateVO;
import com.edu.aitutor.module.qr.vo.QrMockUserVO;
import com.edu.aitutor.module.qr.vo.QrStatusVO;

import java.util.List;

/**
 * 模拟扫码登录服务（接口文档 3.6~3.9）。
 */
public interface QrLoginService {

    /** 生成二维码（3.6） */
    QrCreateVO create();

    /** 轮询状态（3.7） */
    QrStatusVO status(String qrToken);

    /** 模拟扫码 + 白名单校验（3.8） */
    void scan(QrScanDTO dto);

    /** 确认登录（3.9） */
    void confirm(String qrToken);

    /** 取消登录（3.9） */
    void cancel(String qrToken);

    /** 模拟手机候选账号（3.9，仅 dev） */
    List<QrMockUserVO> mockUsers();
}
