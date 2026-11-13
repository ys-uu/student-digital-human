-- ============================================================
-- AI助教系统 v1.3 增量脚本：RAG 知识库 / 文件元数据 / AI 用量日志
-- ============================================================
-- 版本：v1.3（2026-11-12）
-- 适用：MySQL 8.0；InnoDB；utf8mb4 / utf8mb4_general_ci。
-- 前置：已执行 docs/sql/ai_tutor.sql（表7～表35）与 docs/sql/rbac.sql（4 张 RBAC 表）。
--
-- 变更清单：
--   一、新增 3 张表
--     表36 knowledge_base  知识库表（私有/公共/课程三种可见范围）
--     表37 file_record     统一文件元数据表（MinIO 对象登记）
--     表38 ai_usage_log    AI 调用用量日志表（成本统计与审计）
--   二、ALTER 既有 2 张表
--     1) knowledge_document：新增 kb_id / file_id / ingest_status / ingest_error，
--        course_id 由 NOT NULL 放宽为可空（私有/公共库文档不挂课程）；
--     2) chat_session：新增 kb_id（会话挂载的知识库，本期一个会话挂一个）。
--
-- 执行路径：
--   · 已有数据库：直接执行本文件即可（CREATE TABLE 带 IF NOT EXISTS 语义保护见下，
--     ALTER 语句不可重复执行，MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
--     执行前请确认列与索引不存在，重复执行会报错）；
--   · 全新数据库：先执行 ai_tutor.sql、rbac.sql，再执行本文件。
--
-- 约定沿用：BIGINT UNSIGNED 主键；逻辑删除 is_deleted；外键默认 RESTRICT；
--   仅日志类表的 user 外键使用 ON DELETE SET NULL 保留审计记录。
-- ============================================================
SET NAMES utf8mb4;
USE `ai_tutor`;

-- ============================================================
-- 一、新增表
-- ============================================================

-- 表36：知识库表
-- 可见范围：1 私有（仅所有者），2 公共（全体登录用户），3 课程（课程成员/任课教师）。
-- visibility=3 时 course_id 必须非空，该约束由应用层校验，与现有库风格一致不建 CHECK。
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识库ID，主键',
  `kb_name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
  `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '知识库描述',
  `owner_id` BIGINT UNSIGNED NOT NULL COMMENT '所有者 sys_user.id',
  `visibility` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '可见范围：1 私有库，2 公共库，3 课程库',
  `course_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 course.id，仅课程库使用',
  `doc_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文档数量（冗余计数，由应用维护）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0 停用，1 正常',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_knowledge_base_owner_id` FOREIGN KEY (`owner_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_base_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_visibility` (`visibility`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识库表';

-- 表37：统一文件元数据表
-- 登记 MinIO 中的对象；物理文件存 MinIO，本表只存元数据与对象 key。
-- 业务类型：1 知识库文档，2 聊天媒体，3 头像，4 数字人资源，9 其他。
CREATE TABLE IF NOT EXISTS `file_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件ID，主键',
  `bucket_name` VARCHAR(64) NOT NULL COMMENT 'MinIO 桶名',
  `object_key` VARCHAR(512) NOT NULL COMMENT 'MinIO 对象 key',
  `file_name` VARCHAR(200) NOT NULL COMMENT '原始文件名',
  `file_type` VARCHAR(20) NULL DEFAULT NULL COMMENT '文件扩展名：pdf / docx / txt / md 等',
  `file_size` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '文件大小（字节）',
  `file_hash` VARCHAR(64) NULL DEFAULT NULL COMMENT '文件内容哈希（SHA-256，用于去重校验）',
  `biz_type` TINYINT UNSIGNED NOT NULL DEFAULT 9 COMMENT '业务类型：1 知识库文档，2 聊天媒体，3 头像，4 数字人资源，9 其他',
  `uploader_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '上传人 sys_user.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间（仅追加）',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除；对象清理由应用处理',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_file_record_uploader_id` FOREIGN KEY (`uploader_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_uploader_id` (`uploader_id`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_object_key` (`object_key`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一文件元数据表';

-- 表38：AI 调用用量日志表
-- 仅追加的审计数据，用于成本分析、配额核对与故障排查；用户删除时保留日志，外键置空。
-- session_id / kb_id 不建物理外键：会话与知识库删除不得阻断日志写入，引用有效性由应用保证。
CREATE TABLE IF NOT EXISTS `ai_usage_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键',
  `user_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '调用用户 sys_user.id',
  `session_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 chat_session.id（不建物理外键）',
  `kb_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 knowledge_base.id（不建物理外键）',
  `model_name` VARCHAR(50) NOT NULL COMMENT '模型名称，如 deepseek-v4-flash',
  `scene_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '场景：1 自由答疑，2 拍照搜题，3 作业辅导，4 课堂追问，5 备考',
  `prompt_tokens` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输入 token 数',
  `completion_tokens` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '输出 token 数',
  `total_tokens` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总 token 数',
  `duration_ms` INT UNSIGNED NULL DEFAULT NULL COMMENT '响应耗时（毫秒）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '调用结果：0 失败，1 成功',
  `fail_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '失败原因（状态=0 时填写）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用发生时间（仅追加）',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_ai_usage_log_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_model_name` (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 调用用量日志表';

-- ============================================================
-- 二、ALTER 既有表（升级执行，不可重复执行，执行前先确认列不存在）
-- ============================================================

-- 2.1 knowledge_document：接入知识库实体与统一文件登记，并补充 RAG 入库状态
-- 2.1.1 新增 kb_id（关联知识库）
ALTER TABLE `knowledge_document`
  ADD COLUMN `kb_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '关联 knowledge_base.id' AFTER `chapter_id`,
  ADD CONSTRAINT `fk_knowledge_document_kb_id`
    FOREIGN KEY (`kb_id`) REFERENCES `knowledge_base` (`id`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD KEY `idx_kb_id` (`kb_id`);

-- 2.1.2 新增 file_id（关联统一文件元数据；原 file_url 等字段保留兼容）
ALTER TABLE `knowledge_document`
  ADD COLUMN `file_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '关联 file_record.id' AFTER `file_url`,
  ADD CONSTRAINT `fk_knowledge_document_file_id`
    FOREIGN KEY (`file_id`) REFERENCES `file_record` (`id`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD KEY `idx_file_id` (`file_id`);

-- 2.1.3 course_id 放宽为可空：私有/公共知识库的文档不挂课程
ALTER TABLE `knowledge_document`
  MODIFY COLUMN `course_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '关联 course.id（课程库非空，私有/公共库可空）';

-- 2.1.4 新增 RAG 入库处理状态：0 待处理，1 处理中，2 成功，3 失败
-- 注意与 audit_status（人工审核状态）区分，二者互不替代。
ALTER TABLE `knowledge_document`
  ADD COLUMN `ingest_status` TINYINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT 'RAG 入库状态：0 待处理，1 处理中，2 成功，3 失败' AFTER `audit_status`,
  ADD COLUMN `ingest_error` VARCHAR(255) NULL DEFAULT NULL
    COMMENT '入库失败原因（ingest_status=3 时填写）' AFTER `ingest_status`,
  ADD KEY `idx_ingest_status` (`ingest_status`);

-- 2.2 chat_session：新增挂载知识库
-- 本期一个会话挂一个知识库；未来支持多挂载时按架构文档新增会话-知识库关联表，
-- Qdrant 的 payload 过滤原生支持 kb_id 列表，无需改动向量结构。
ALTER TABLE `chat_session`
  ADD COLUMN `kb_id` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '挂载的知识库 knowledge_base.id' AFTER `kp_id`,
  ADD CONSTRAINT `fk_chat_session_kb_id`
    FOREIGN KEY (`kb_id`) REFERENCES `knowledge_base` (`id`)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  ADD KEY `idx_kb_id` (`kb_id`);

-- ============================================================
-- 三、执行后核对：应为 36 张表（原 33 张 + 本次 3 张）
-- ============================================================
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'ai_tutor' AND table_type = 'BASE TABLE';

-- 列核对（应分别返回 1）：
-- SELECT COUNT(*) FROM information_schema.columns
--   WHERE table_schema='ai_tutor' AND table_name='knowledge_document' AND column_name='ingest_status';
-- SELECT COUNT(*) FROM information_schema.columns
--   WHERE table_schema='ai_tutor' AND table_name='chat_session' AND column_name='kb_id';
