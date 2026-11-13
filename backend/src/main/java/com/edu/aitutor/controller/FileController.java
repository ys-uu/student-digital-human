package com.edu.aitutor.controller;

import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.common.Result;
import com.edu.aitutor.dto.PresignDTO;
import com.edu.aitutor.entity.FileRecord;
import com.edu.aitutor.service.FileService;
import com.edu.aitutor.vo.DownloadUrlVO;
import com.edu.aitutor.vo.PresignVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件存储接口：预签名直传、上传确认、预签名下载、文件列表，对齐接口文档 17.3。
 * 均需登录，文件归属由服务层按当前用户校验。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @Resource
    private FileService fileService;

    /** 17.3.1 申请预签名上传 URL */
    @PostMapping("/presign")
    public Result<PresignVO> presign(@Valid @RequestBody PresignDTO dto) {
        return Result.success(fileService.presign(dto));
    }

    /** 17.3.2 上传完成确认 */
    @PostMapping("/{id}/confirm")
    public Result<FileRecord> confirm(@PathVariable Long id,
                                      @RequestParam(required = false) String fileHash) {
        return Result.success(fileService.confirm(id, fileHash));
    }

    /** 17.3.3 申请预签名下载 URL */
    @GetMapping("/{id}/download-url")
    public Result<DownloadUrlVO> downloadUrl(@PathVariable Long id) {
        return Result.success(fileService.downloadUrl(id));
    }

    /** 17.3.4 文件列表 */
    @GetMapping
    public Result<PageResult<FileRecord>> page(
            @RequestParam(required = false) Integer bizType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        return Result.success(fileService.page(bizType, keyword, page, pageSize));
    }
}
