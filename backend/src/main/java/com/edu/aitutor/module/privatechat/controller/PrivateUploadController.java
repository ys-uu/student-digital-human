package com.edu.aitutor.module.privatechat.controller;

import com.edu.aitutor.common.BusinessException;
import com.edu.aitutor.common.ErrorCode;
import com.edu.aitutor.common.Result;
import com.edu.aitutor.framework.security.SecurityUtils;
import com.edu.aitutor.module.file.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 私信媒体上传接口（v1.9 新增）。
 *
 * ============================================================================
 * 【为什么用"预签名直传"而不是"服务端中转"】
 * ----------------------------------------------------------------------------
 * 两种做法对比：
 *
 *   A. 服务端中转：客户端 → 后端 → MinIO
 *      · 图片/音频动辄几 MB，全部流量压在后端，占用 Tomcat 线程与内存；
 *      · 上传 10MB 文件要占用连接十几秒，并发几十人就会拖垮服务。
 *
 *   B. 预签名直传（本项目采用）：后端只签发一个有时效的上传地址，
 *      客户端拿到后直接把文件 PUT 到 MinIO，文件流完全不经过后端。
 *      · 后端只处理一个几百字节的签名请求，几乎零成本；
 *      · 上传速度取决于客户端到 MinIO 的带宽，不受后端限制；
 *      · 签名里绑定了固定的对象键，客户端无法越权写到别的路径。
 *
 * ============================================================================
 * 【完整时序（前端对接方式）】
 * ----------------------------------------------------------------------------
 * <pre>
 * ① GET /api/v1/private/upload-url?fileName=a.jpg&contentType=image/jpeg
 *    → { uploadUrl, objectKey, accessUrl, expiresIn }
 *
 * ② 客户端 PUT uploadUrl，body 为文件二进制
 *    （PUT 时要带上与签发时一致的 Content-Type，否则 MinIO 验签失败）
 *
 * ③ 发送消息时把 accessUrl 作为 media_url 传给 private_msg 报文
 *    消息落库后，对方即可通过该 URL 看到图片/播放音频。
 * </pre>
 *
 * ============================================================================
 * 【访问地址的有效期设计】
 * ----------------------------------------------------------------------------
 * 返回的 accessUrl 是预签名 GET 地址，默认有效期 7 天（私信场景足够）。
 * 之所以不直接用公开桶：私信内容属于隐私数据，公开桶等于对全网开放。
 * 若消息超过有效期后仍需访问，可再次调用本接口换取新的 GET 地址
 * （objectKey 已存在数据库的 mediaUrl 中，可解析出）。
 * ============================================================================
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/private")
public class PrivateUploadController {

    /**
     * 允许上传的媒体类型 → 对应的扩展名与大小上限。
     * <p>
     * 白名单而非黑名单：只放行明确支持的格式，避免用户上传可执行文件
     * 或构造畸形 MIME 绕过检查。
     */
    private static final Set<String> ALLOWED_IMAGE = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private static final Set<String> ALLOWED_AUDIO = Set.of(
            "audio/mpeg", "audio/mp4", "audio/aac", "audio/ogg", "audio/wav", "audio/webm",
            // 浏览器对同一格式可能上报不同 MIME 别名，这里补充以兼容：
            // .wav 在部分环境下是 audio/x-wav / audio/wave；
            // .m4a 常被识别为 audio/x-m4a；
            // .mp3 偶见 audio/mp3。
            "audio/x-wav", "audio/wave", "audio/x-m4a", "audio/mp3", "audio/x-mpeg");

    /** 图片上限 10MB：手机原图通常在 2~8MB，压缩后更小 */
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    /** 音频上限 20MB：约 10 分钟的标准码率语音 */
    private static final long MAX_AUDIO_SIZE = 20L * 1024 * 1024;

    private final FileStorageService storageService;

    /** 媒体文件存放的桶，复用已有的 documents 桶 */
    @Value("${minio.default-bucket:documents}")
    private String bucket;

    /** GET 地址有效期（秒），默认 7 天 */
    @Value("${privatechat.media-url-expiry:604800}")
    private long mediaUrlExpiry;

    public PrivateUploadController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 申请媒体上传地址。
     *
     * @param fileName    原始文件名，仅用于取扩展名
     * @param contentType 文件的 MIME 类型，必须在白名单内
     * @param size        文件字节数，可选；给出时会校验上限
     * @return uploadUrl（PUT 用）、accessUrl（消息里存的地址）、objectKey 等
     */
    @GetMapping("/upload-url")
    public Result<Map<String, Object>> uploadUrl(
            @RequestParam String fileName,
            @RequestParam String contentType,
            @RequestParam(required = false) Long size) {

        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.WS_UNAUTHORIZED, "未登录");
        }

        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        boolean isImage = ALLOWED_IMAGE.contains(mime);
        boolean isAudio = ALLOWED_AUDIO.contains(mime);
        if (!isImage && !isAudio) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "仅支持图片（jpg/png/gif/webp）与音频（mp3/aac/ogg/wav/webm）");
        }
        long maxSize = isImage ? MAX_IMAGE_SIZE : MAX_AUDIO_SIZE;
        if (size != null && size > maxSize) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    (isImage ? "图片" : "音频") + "不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }

        // 对象键：私信/用户ID/日期/随机文件名.扩展名
        // 加日期分目录便于运维按时间归档或清理；随机名避免同名覆盖。
        String ext = extensionOf(fileName, mime);
        String objectKey = String.format("private/%d/%s/%s.%s",
                userId, LocalDate.now(), UUID.randomUUID().toString().replace("-", ""), ext);

        String uploadUrl = storageService.presignedPutUrl(bucket, objectKey, Duration.ofMinutes(30));
        String accessUrl = storageService.presignedGetUrl(bucket, objectKey, Duration.ofSeconds(mediaUrlExpiry));

        Map<String, Object> data = new HashMap<>();
        data.put("uploadUrl", uploadUrl);      // 客户端 PUT 到这里
        data.put("accessUrl", accessUrl);      // 发送消息时作为 media_url
        data.put("objectKey", objectKey);      // 便于日志排查与后续换签名
        data.put("mediaType", isImage ? "image" : "audio");
        data.put("maxSize", maxSize);
        // 提醒前端 PUT 时必须带同样的 Content-Type，否则 MinIO 验签会失败
        data.put("contentType", mime);
        data.put("expiresIn", mediaUrlExpiry);

        log.info("签发私信媒体上传地址 userId={} type={} key={}", userId, mime, objectKey);
        return Result.success(data);
    }

    /**
     * 取文件扩展名。
     * <p>
     * 优先用原始文件名的扩展名；若文件名没有扩展名（如直接粘贴的图片），
     * 则按 MIME 类型推断，保证对象键带正确后缀，MinIO 才能给出正确的 Content-Type。
     */
    private String extensionOf(String fileName, String mime) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0 && dot < fileName.length() - 1) {
                String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                // 只接受纯字母扩展名，防止用户传入奇怪字符影响对象键
                if (ext.matches("[a-z0-9]{1,8}")) {
                    return ext;
                }
            }
        }
        return switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "audio/mpeg" -> "mp3";
            case "audio/mp3" -> "mp3";
            case "audio/x-mpeg" -> "mp3";
            case "audio/mp4" -> "m4a";
            case "audio/x-m4a" -> "m4a";
            case "audio/aac" -> "aac";
            case "audio/ogg" -> "ogg";
            case "audio/wav" -> "wav";
            case "audio/x-wav" -> "wav";
            case "audio/wave" -> "wav";
            case "audio/webm" -> "webm";
            default -> "bin";
        };
    }
}