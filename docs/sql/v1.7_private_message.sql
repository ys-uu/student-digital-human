-- ============================================================
-- AI助教系统 v1.7 增量脚本：WebSocket 1对1实时对话（私信）
-- ============================================================
-- 版本：v1.7（2026-11-21）
-- 适用：MySQL 8.0；InnoDB；utf8mb4 / utf8mb4_general_ci。
-- 前置：已执行 docs/sql/ai_tutor.sql（表7～表35）、rbac.sql、
--       v1.3_rag_increment.sql；v1.6_create_teacher_accounts.sql 与本脚本无依赖关系。
--
-- 变更清单：
--   一、新增 2 张表
--     表36 private_conversation  私信会话表（一对一，user_a_id < user_b_id 归一化）
--     表37 private_message       私信消息表（仅追加，离线补拉的数据来源）
--   二、不修改任何既有表
--     class_info / class_member / course / notification 仅被读取，
--     师生关系判定完全复用既有数据，不新增关系表。
--
-- 对应接口文档：第 18 章 v1.7 WebSocket 1对1实时对话
--   · 18.4 WebSocket 报文（private_msg / private_ack / private_recv / private_read / sync）
--   · 18.5 HTTP 兜底接口（6 个）
--   · 18.8 数据库变更（本脚本即其实体实现）
--
-- 执行路径：
--   · 已有数据库：直接执行本文件即可（CREATE TABLE IF NOT EXISTS，可重复执行不报错）；
--   · 全新数据库：先执行 ai_tutor.sql、rbac.sql、v1.3_rag_increment.sql，再执行本文件；
--   · 本脚本无 ALTER 语句，不存在"不可重复执行"的限制。
--
-- 约定沿用（与既有脚本一致）：
--   BIGINT UNSIGNED 主键；逻辑删除 is_deleted；外键默认 RESTRICT；
--   仅日志类/消息类表只保留 create_time。
--
-- 幂等说明：
--   uk_client(sender_id, client_msg_id)       客户端重发去重，对应 private_msg 的幂等语义；
--   uk_pair(user_a_id, user_b_id)             同一对用户只允许一条会话，配合 a<b 归一化；
--   uk_conversation_no(conversation_no)       会话编号唯一。
-- ============================================================
SET NAMES utf8mb4;
USE `ai_tutor`;

-- ------------------------------------------------------------
-- 表36：私信会话表
-- 设计要点：
--   1) user_a_id < user_b_id 由应用层保证（写入前按 min/max 归一化），
--      配合 uk_pair 使 A 找 B 与 B 找 A 落在同一行，避免重复会话；
--   2) 未读数与已读游标按 a/b 拆分为两组列，避免"每会话每人一行"的成员表；
--   3) 冗余 last_message_* 供会话列表直接排序展示，列表查询无需 join 消息表；
--   4) 不存"关系类型"，权限判定每次实时按 class_member + course 计算，
--      避免班级/课程调整后权限不同步。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `private_conversation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键',
  `conversation_no` VARCHAR(32) NOT NULL COMMENT '会话编号（UUID），唯一',
  `user_a_id` BIGINT UNSIGNED NOT NULL COMMENT '对话方A，约定 user_a_id < user_b_id',
  `user_b_id` BIGINT UNSIGNED NOT NULL COMMENT '对话方B',
  `last_message_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后一条消息ID private_message.id',
  `last_message_at` DATETIME NULL DEFAULT NULL COMMENT '最后一条消息时间，会话列表排序依据',
  `last_message_preview` VARCHAR(200) NULL DEFAULT NULL COMMENT '最后一条消息摘要（截断存储）',
  `a_unread_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'A 的未读消息数',
  `b_unread_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'B 的未读消息数',
  `a_last_read_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'A 已读到的最新消息ID（游标）',
  `b_last_read_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'B 已读到的最新消息ID（游标）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_private_conversation_user_a` FOREIGN KEY (`user_a_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_private_conversation_user_b` FOREIGN KEY (`user_b_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_conversation_no` (`conversation_no`),
  UNIQUE KEY `uk_pair` (`user_a_id`, `user_b_id`),
  KEY `idx_user_a` (`user_a_id`),
  KEY `idx_user_b` (`user_b_id`),
  KEY `idx_last_message_at` (`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='私信会话表';

-- ------------------------------------------------------------
-- 表37：私信消息表
-- 设计要点：
--   1) 仅追加不修改，故只保留 create_time（与 chat_message 约定一致）；
--   2) client_msg_id + uk_client 实现客户端幂等：断线重连重发、用户双击，
--      均由数据库唯一约束兜底，不依赖业务判重；
--   3) receiver_id 冗余存储，使"上线补拉"（sync）可按接收方 + 游标直接走索引；
--   4) idx_conv_id(conversation_id, id) 支撑消息历史的游标分页（before_id）；
--   5) idx_receiver_unread(receiver_id, read_at) 支撑未读统计与已读批量更新；
--   6) recall_status / report_status 为后续扩展预留，本期仅写默认值。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `private_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID，主键，兼作游标分页的游标',
  `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 private_conversation.id',
  `sender_id` BIGINT UNSIGNED NOT NULL COMMENT '发送者 user_id',
  `receiver_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者 user_id（冗余，便于补拉）',
  `content` TEXT NOT NULL COMMENT '消息文本内容',
  `msg_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '消息类型：1 文本，2 图片，3 文件',
  `media_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '图片/文件 URL（msg_type != 1 时使用）',
  `client_msg_id` VARCHAR(64) NOT NULL COMMENT '客户端生成的幂等ID（UUID）',
  `delivered_at` DATETIME NULL DEFAULT NULL COMMENT '送达时间（在线推送成功时回写）',
  `read_at` DATETIME NULL DEFAULT NULL COMMENT '已读时间',
  `recall_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '撤回状态：0 正常，1 已撤回（预留）',
  `report_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '举报状态：0 无，1 已举报（预留）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间（仅追加，不修改）',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_private_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `private_conversation` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_private_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_private_message_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_client` (`sender_id`, `client_msg_id`),
  KEY `idx_conv_id` (`conversation_id`, `id`),
  KEY `idx_receiver_unread` (`receiver_id`, `read_at`),
  KEY `idx_sender_id` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='私信消息表';

-- ============================================================
-- 附：师生关系判定参考 SQL（仅说明用途，不建立视图，由应用层 Mapper 实现）
-- ============================================================
-- 判定 userA 与 userB 是否构成"同老师 + 同课程"的师生关系：
--   存在一门课 c，使 c.teacher_id 为其中一方教师，
--   且另一方学生在 c.class_id 对应班级中（class_member.member_type = 1）。
--
-- SELECT COUNT(*) FROM (
--     SELECT 1
--     FROM course c
--     JOIN class_member cm ON cm.class_id = c.class_id
--                         AND cm.user_id = {studentId}
--                         AND cm.member_type = 1
--                         AND cm.is_deleted = 0
--     WHERE c.teacher_id = {teacherId}
--       AND c.is_deleted = 0
--       AND c.status = 1
-- ) t;
--
-- 说明：两侧角色顺序由应用层按 sys_user.role 判断后决定 {studentId}/{teacherId}，
--       上表仅给出"教师任课且学生在班"这一条核心条件。

-- ============================================================
-- 验证语句（执行后可手工核对，也可用于自动化检查）
-- ============================================================
-- SHOW CREATE TABLE `private_conversation`;
-- SHOW CREATE TABLE `private_message`;
-- SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = 'ai_tutor'
--     AND TABLE_NAME IN ('private_conversation', 'private_message');