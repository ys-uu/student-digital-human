package com.edu.aitutor.service;

import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.dto.PresignDTO;
import com.edu.aitutor.entity.FileRecord;
import com.edu.aitutor.vo.DownloadUrlVO;
import com.edu.aitutor.vo.PresignVO;

/**
 * 文件业务编排：预签名申请、上传确认、下载 URL、分页查询。
 */
public interface FileService {

    /** 申请预签名上传 URL 并登记元数据 */
    PresignVO presign(PresignDTO dto);

    /** 上传完成确认，校验对象已存在，可选回写哈希 */
    FileRecord confirm(Long id, String fileHash);

    /** 申请预签名下载 URL */
    DownloadUrlVO downloadUrl(Long id);

    /** 分页查询当前用户的文件 */
    PageResult<FileRecord> page(Integer bizType, String keyword, int page, int size);
}
