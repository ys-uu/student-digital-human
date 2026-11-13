package com.edu.aitutor.service.impl;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.PageResult;
import com.edu.aitutor.config.MinioProperties;
import com.edu.aitutor.dto.PresignDTO;
import com.edu.aitutor.entity.FileRecord;
import com.edu.aitutor.mapper.FileRecordMapper;
import com.edu.aitutor.security.SecurityUtils;
import com.edu.aitutor.service.FileService;
import com.edu.aitutor.service.FileStorageService;
import com.edu.aitutor.vo.DownloadUrlVO;
import com.edu.aitutor.vo.PresignVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件业务编排实现：对象 key 生成、元数据登记、归属校验与预签名签发。
 */
@Service
public class FileServiceImpl implements FileService {

    /** 合法业务类型 */
    private static final Set<Integer> VALID_BIZ_TYPES = Set.of(1, 2, 3, 4, 9);

    /** 业务类型 → 桶 */
    private static final Map<Integer, String> BUCKET_MAP = Map.of(
            1, "documents",
            2, "chat-media",
            3, "avatars",
            4, "documents",
            9, "documents");

    /** 业务类型 → 对象 key 前缀 */
    private static final Map<Integer, String> PREFIX_MAP = Map.of(
            1, "kb",
            2, "chat",
            3, "avatar",
            4, "digital-human",
            9, "misc");

    @Resource
    private FileRecordMapper fileRecordMapper;
    @Resource
    private FileStorageService fileStorageService;
    @Resource
    private MinioProperties minioProperties;

    @Override
    public PresignVO presign(PresignDTO dto) {
        if (!VALID_BIZ_TYPES.contains(dto.getBizType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "业务类型非法");
        }
        Long uploaderId = SecurityUtils.currentUserId();
        String fileType = normalizeType(dto.getFileType());
        String bucket = BUCKET_MAP.get(dto.getBizType());
        String objectKey = buildObjectKey(dto.getBizType(), fileType);

        FileRecord record = new FileRecord();
        record.setBucketName(bucket);
        record.setObjectKey(objectKey);
        record.setFileName(dto.getFileName());
        record.setFileType(fileType);
        record.setFileSize(dto.getFileSize());
        record.setBizType(dto.getBizType());
        record.setUploaderId(uploaderId);
        fileRecordMapper.insert(record);

        Duration expiry = Duration.ofSeconds(minioProperties.getPresignExpiry());
        String uploadUrl = fileStorageService.presignedPutUrl(bucket, objectKey, expiry);

        PresignVO vo = new PresignVO();
        vo.setFileId(record.getId());
        vo.setBucketName(bucket);
        vo.setObjectKey(objectKey);
        vo.setUploadUrl(uploadUrl);
        vo.setExpiresIn(minioProperties.getPresignExpiry());
        return vo;
    }

    @Override
    public FileRecord confirm(Long id, String fileHash) {
        FileRecord record = getOwnedRecord(id);
        if (!fileStorageService.objectExists(record.getBucketName(), record.getObjectKey())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对象尚未上传或已被清理，无法确认");
        }
        if (fileHash != null && !fileHash.isBlank()) {
            String hash = fileHash.trim();
            fileRecordMapper.updateHash(id, hash);
            record.setFileHash(hash);
        }
        return record;
    }

    @Override
    public DownloadUrlVO downloadUrl(Long id) {
        FileRecord record = getOwnedRecord(id);
        Duration expiry = Duration.ofSeconds(minioProperties.getPresignExpiry());
        String url = fileStorageService.presignedGetUrl(
                record.getBucketName(), record.getObjectKey(), expiry);
        DownloadUrlVO vo = new DownloadUrlVO();
        vo.setDownloadUrl(url);
        vo.setExpiresIn(minioProperties.getPresignExpiry());
        return vo;
    }

    @Override
    public PageResult<FileRecord> page(Integer bizType, String keyword, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分页参数非法");
        }
        Long uploaderId = SecurityUtils.currentUserId();
        int offset = (page - 1) * size;
        long total = fileRecordMapper.countPage(uploaderId, bizType, keyword);
        var list = fileRecordMapper.selectPage(uploaderId, bizType, keyword, offset, size);
        return new PageResult<>(total, list);
    }

    /** 取记录并校验存在与归属 */
    private FileRecord getOwnedRecord(Long id) {
        FileRecord record = fileRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        if (!record.getUploaderId().equals(SecurityUtils.currentUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作他人文件");
        }
        return record;
    }

    /** 规范化扩展名：去前导点、小写 */
    private String normalizeType(String raw) {
        String t = raw.trim().toLowerCase();
        if (t.startsWith(".")) {
            t = t.substring(1);
        }
        return t;
    }

    /** 生成对象 key：前缀/年/月/uuid.扩展名 */
    private String buildObjectKey(Integer bizType, String fileType) {
        LocalDate now = LocalDate.now();
        String month = String.format("%02d", now.getMonthValue());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return PREFIX_MAP.get(bizType) + "/" + now.getYear() + "/" + month + "/"
                + uuid + "." + fileType;
    }
}
