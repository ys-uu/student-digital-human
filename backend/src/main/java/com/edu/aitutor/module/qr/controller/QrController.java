package com.edu.aitutor.module.qr.controller;

import com.edu.aitutor.common.Result;
import com.edu.aitutor.module.qr.dto.QrScanDTO;
import com.edu.aitutor.module.qr.dto.QrTokenDTO;
import com.edu.aitutor.module.qr.service.QrLoginService;
import com.edu.aitutor.module.qr.vo.QrCreateVO;
import com.edu.aitutor.module.qr.vo.QrMockUserVO;
import com.edu.aitutor.module.qr.vo.QrStatusVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模拟扫码登录接口（接口文档 3.6~3.9），路径无 /api/v1 前缀，与文档一致。
 */
@RestController
@RequestMapping("/qr")
public class QrController {

    @Resource
    private QrLoginService qrLoginService;

    /** 3.6 生成二维码 */
    @PostMapping("/create")
    public Result<QrCreateVO> create() {
        return Result.success(qrLoginService.create());
    }

    /** 3.7 轮询状态 */
    @GetMapping("/status")
    public Result<QrStatusVO> status(@RequestParam("qrToken") String qrToken) {
        return Result.success(qrLoginService.status(qrToken));
    }

    /** 3.8 模拟扫码（白名单校验） */
    @PostMapping("/scan")
    public Result<Map<String, String>> scan(@Valid @RequestBody QrScanDTO dto) {
        qrLoginService.scan(dto);
        return Result.success(Map.of("tip", "扫码成功，请在手机上确认登录"));
    }

    /** 3.9 确认登录 */
    @PostMapping("/confirm")
    public Result<Map<String, String>> confirm(@Valid @RequestBody QrTokenDTO dto) {
        qrLoginService.confirm(dto.getQrToken());
        return Result.success(Map.of("tip", "登录成功"));
    }

    /** 3.9 取消登录 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@Valid @RequestBody QrTokenDTO dto) {
        qrLoginService.cancel(dto.getQrToken());
        return Result.success(null);
    }

    /** 3.9 模拟手机候选账号（仅 dev 环境） */
    @GetMapping("/mock-users")
    public Result<List<QrMockUserVO>> mockUsers() {
        return Result.success(qrLoginService.mockUsers());
    }
}
