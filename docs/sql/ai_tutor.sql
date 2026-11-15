-- AI助教系统：依据需求分析说明书表7～表33，27张表（v1.2 扩展为 29 张，见文件末尾表34、表35）。
-- v1.2（2026-11-09）：新增登录方式支持（手机验证码登录 + 模拟扫码登录），
--   共 27 + 2 = 29 张表；sys_user 增加 phone 唯一索引、password_hash 改可空、新增 phone_verified。
--   新增表：login_whitelist（扫码白名单）、auth_login_log（登录审计日志）。
--   说明：短信验证码由阿里云号码认证服务（SendSmsVerifyCode/CheckSmsVerifyCode）托管，不建验证码表；
--        扫码 qr_token 为一次性凭证（TTL 120s），存 Redis / 内存，不建表。
-- 适用 MySQL 8.0；UTF-8；InnoDB；utf8mb4_general_ci。
-- 面向新数据库；不删除已有表，不生成虚构账号或业务数据。
-- 已有同名表时 CREATE TABLE 会报错，请勿作为数据库升级脚本使用（升级用文末附录的 ALTER 脚本）。
-- 特殊值约定：chapter.parent_id=0 为顶级章节；avatar_config.user_id=0 为系统模板；
-- study_stat_daily.course_id=0 为跨课程统计。上述三处不建立物理外键，非零引用由业务层校验。
-- 其余外键使用 RESTRICT，逻辑删除和业务级跨表一致性由应用维护。
-- 联合唯一键严格按文档设置；逻辑删除后恢复/更新原记录，避免重复插入。
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS `ai_tutor` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `ai_tutor`;

-- 表7：用户账号表
-- v1.2 变更：
--   1) password_hash 改为可空 —— 纯手机验证码注册的用户没有密码，NOT NULL 会导致插入失败；
--   2) phone 增加唯一键 uk_phone(phone, is_deleted) —— 验证码登录靠手机号反查账号，必须唯一；
--      采用 (phone, is_deleted) 复合唯一，避免逻辑删除的账号永久占用手机号；
--      MySQL 唯一索引允许多个 NULL，未填手机号的账号不受影响；
--   3) 新增 phone_verified —— 区分教务导入的未验证号码与用户自行验证的号码。
CREATE TABLE `sys_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键',
  `user_no` VARCHAR(32) NOT NULL COMMENT '登录账号（学号 / 工号），唯一',
  `password_hash` VARCHAR(128) NULL DEFAULT NULL COMMENT '登录密码哈希值（bcrypt 加密，不存明文）；纯手机验证码用户可为空',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `role` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '角色：1 学生，2 教师，3 教务管理员',
  `gender` TINYINT UNSIGNED NULL DEFAULT 0 COMMENT '性别：0 未知，1 男，2 女',
  `phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '手机号（验证码登录凭据，唯一；建议加密存储）',
  `phone_verified` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '手机号是否已通过验证码校验：0 否，1 是',
  `email` VARCHAR(100) NULL DEFAULT NULL COMMENT '电子邮箱',
  `avatar_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '头像文件 URL',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '账号状态：0 禁用，1 正常',
  `last_login_at` DATETIME NULL DEFAULT NULL COMMENT '最近一次登录时间',
  `last_login_ip` VARCHAR(45) NULL DEFAULT NULL COMMENT '最近登录 IP（兼容 IPv6）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_no` (`user_no`),
  UNIQUE KEY `uk_phone` (`phone`, `is_deleted`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户账号表';

-- 表8：学生档案表
-- 注：与 sys_user 为 1:1 关系。
CREATE TABLE `student_profile` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '档案ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 sys_user.id（1:1）',
  `student_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '学号',
  `college` VARCHAR(100) NULL DEFAULT NULL COMMENT '所属学院',
  `major` VARCHAR(100) NULL DEFAULT NULL COMMENT '所学专业',
  `grade` VARCHAR(10) NULL DEFAULT NULL COMMENT '年级，如 2024',
  `class_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '自然班名称',
  `enrollment_year` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '入学年份',
  `exam_target` VARCHAR(200) NULL DEFAULT NULL COMMENT '备考目标（考研 / 考证等）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_student_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_major` (`major`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学生档案表';

-- 表9：教师档案表
-- 注：与 sys_user 为 1:1 关系。
CREATE TABLE `teacher_profile` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '档案ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 sys_user.id（1:1）',
  `teacher_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '工号',
  `college` VARCHAR(100) NULL DEFAULT NULL COMMENT '所属学院',
  `department` VARCHAR(100) NULL DEFAULT NULL COMMENT '系 / 教研室',
  `title` VARCHAR(50) NULL DEFAULT NULL COMMENT '职称（教授 / 副教授 / 讲师等）',
  `research_direction` VARCHAR(200) NULL DEFAULT NULL COMMENT '研究方向',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_teacher_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_teacher_no` (`teacher_no`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='教师档案表';

-- 表10：班级表
CREATE TABLE `class_info` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '班级ID，主键',
  `class_name` VARCHAR(50) NOT NULL COMMENT '班级名称',
  `class_code` VARCHAR(32) NOT NULL COMMENT '班级编码，唯一',
  `college` VARCHAR(100) NULL DEFAULT NULL COMMENT '所属学院',
  `grade` VARCHAR(10) NULL DEFAULT NULL COMMENT '年级',
  `owner_teacher_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '负责（班主任）教师 user_id',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0 停用，1 正常',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_class_info_owner_teacher_id` FOREIGN KEY (`owner_teacher_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_class_code` (`class_code`),
  KEY `idx_owner_teacher_id` (`owner_teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班级表';

-- 表11：班级成员表
-- 注：联合唯一键 uk_class_user(class_id, user_id, member_type)，防止同一成员重复加入。
CREATE TABLE `class_member` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员记录ID，主键',
  `class_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 class_info.id',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 sys_user.id',
  `member_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '成员类型：1 学生，2 任课教师，3 助教',
  `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入班级时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_class_member_class_id` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_class_member_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_class_user` (`class_id`, `user_id`, `member_type`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班级成员表';

-- 表12：课程表
CREATE TABLE `course` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '课程ID，主键',
  `course_code` VARCHAR(32) NOT NULL COMMENT '课程编码，唯一',
  `course_name` VARCHAR(100) NOT NULL COMMENT '课程名称',
  `college` VARCHAR(100) NULL DEFAULT NULL COMMENT '开课学院',
  `credit` DECIMAL(3,1) NULL DEFAULT NULL COMMENT '学分',
  `course_category` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '课程类别：1 公共课，2 专业课，3 选修课',
  `subject_type` TINYINT UNSIGNED NULL DEFAULT 6 COMMENT '学科门类：1 理工，2 文史，3 经管，4 艺术，5 教育，6 其他',
  `class_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '开课班级 class_info.id',
  `teacher_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '主讲教师 user_id',
  `cover_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '课程封面 URL',
  `intro` TEXT NULL DEFAULT NULL COMMENT '课程简介',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0 停开，1 正常',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_course_class_id` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_course_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_course_code` (`course_code`),
  KEY `idx_course_name` (`course_name`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程表';

-- 表13：章节表
-- 注：联合唯一键 uk_course_chapter(course_id, chapter_no)；parent_id 自引用实现多级章节目录。
-- parent_id 的非零值应引用本表id；同时检查父子章节属于同一课程、无目录环。
CREATE TABLE `chapter` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '章节ID，主键',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 course.id',
  `chapter_no` VARCHAR(20) NOT NULL COMMENT '章节号，如 1.2、3.1.1',
  `chapter_name` VARCHAR(100) NOT NULL COMMENT '章节名称',
  `parent_id` BIGINT UNSIGNED NULL DEFAULT 0 COMMENT '父章节ID，0 为顶级章节',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同级排序号',
  `suggest_minutes` INT NULL DEFAULT NULL COMMENT '建议学习时长（分钟）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_chapter_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_course_chapter` (`course_id`, `chapter_no`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='章节表';

-- 表14：知识点表
CREATE TABLE `knowledge_point` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '知识点ID，主键',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 course.id',
  `chapter_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 chapter.id',
  `kp_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '知识点编码',
  `kp_name` VARCHAR(100) NOT NULL COMMENT '知识点名称',
  `is_key` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否重点：0 否，1 是',
  `is_hard` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否难点：0 否，1 是',
  `is_exam_point` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否考点：0 否，1 是',
  `is_error_prone` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否易错点：0 否，1 是',
  `importance` TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '重要度，取值 1～5',
  `mastery_threshold` TINYINT UNSIGNED NOT NULL DEFAULT 60 COMMENT '掌握达标阈值（0～100 分）',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_knowledge_point_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_point_chapter_id` FOREIGN KEY (`chapter_id`) REFERENCES `chapter` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_course_id` (`course_id`),
  KEY `idx_chapter_id` (`chapter_id`),
  KEY `idx_is_key` (`is_key`),
  KEY `idx_is_hard` (`is_hard`),
  KEY `idx_is_exam_point` (`is_exam_point`),
  KEY `idx_is_error_prone` (`is_error_prone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识点表';

-- 表15：知识文档表
CREATE TABLE `knowledge_document` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档ID，主键',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 course.id',
  `chapter_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 chapter.id',
  `doc_name` VARCHAR(200) NOT NULL COMMENT '文档名称（教材 / 课件 / 真题等）',
  `doc_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '类型：1 教材，2 课件，3 题库，4 真题，5 教师补充',
  `file_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '原始文件 URL',
  `file_type` VARCHAR(20) NULL DEFAULT NULL COMMENT '文件类型：pdf / ppt / pptx / docx 等',
  `file_size` INT UNSIGNED NULL DEFAULT NULL COMMENT '文件大小（字节）',
  `page_count` INT NULL DEFAULT NULL COMMENT '页数',
  `uploader_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '上传人 user_id',
  `version_no` VARCHAR(20) NOT NULL DEFAULT 'v1.0' COMMENT '版本号',
  `audit_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '审核状态：0 待审核，1 通过，2 驳回',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_knowledge_document_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_document_chapter_id` FOREIGN KEY (`chapter_id`) REFERENCES `chapter` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_document_uploader_id` FOREIGN KEY (`uploader_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_course_id` (`course_id`),
  KEY `idx_chapter_id` (`chapter_id`),
  KEY `idx_doc_type` (`doc_type`),
  KEY `idx_uploader_id` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识文档表';

-- 表16：知识切片表
-- 注：文本向量（embedding）存储于专业向量数据库（如 Milvus），本表仅以 vector_id 关联引用。
CREATE TABLE `knowledge_chunk` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '切片ID，主键',
  `document_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 knowledge_document.id',
  `kp_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 knowledge_point.id',
  `course_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '冗余课程ID，加速检索',
  `chunk_index` INT NOT NULL DEFAULT 0 COMMENT '文档内切片序号',
  `content` TEXT NOT NULL COMMENT '切片正文',
  `content_summary` VARCHAR(500) NULL DEFAULT NULL COMMENT '内容摘要',
  `source_page` INT NULL DEFAULT NULL COMMENT '来源页码',
  `token_count` INT NULL DEFAULT NULL COMMENT '切片 token 数',
  `vector_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '向量库中的向量ID（embedding 引用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_knowledge_chunk_document_id` FOREIGN KEY (`document_id`) REFERENCES `knowledge_document` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_chunk_kp_id` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_chunk_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_document_id` (`document_id`),
  KEY `idx_kp_id` (`kp_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识切片表';

-- 表17：数字人配置表
-- user_id 的非零值应引用 sys_user.id；同一学生的默认配置唯一性由应用维护。
CREATE TABLE `avatar_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属学生 user_id，0 为系统默认模板',
  `config_name` VARCHAR(50) NOT NULL COMMENT '配置名称',
  `avatar_style` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '形象风格：1 学术型，2 活泼型，3 极简型',
  `avatar_image_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '数字人形象 / 模型资源 URL',
  `voice_id` VARCHAR(32) NULL DEFAULT NULL COMMENT 'TTS 音色ID',
  `tone_style` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '语气风格：1 温和，2 活泼，3 严谨',
  `speech_rate` DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '语速倍率（0.50～2.00）',
  `action_config` JSON NULL DEFAULT NULL COMMENT '动作库与触发条件配置',
  `subject_style` VARCHAR(50) NULL DEFAULT NULL COMMENT '学科风格（数学 / 英语等）',
  `is_default` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为该学生默认配置：0 否，1 是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数字人配置表';

-- 表18：对话会话表
CREATE TABLE `chat_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键',
  `session_no` VARCHAR(32) NOT NULL COMMENT '会话编号（UUID），唯一',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `course_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 course.id',
  `kp_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 knowledge_point.id',
  `scene_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '场景：1 自由答疑，2 拍照搜题，3 作业辅导，4 课堂追问，5 备考',
  `title` VARCHAR(100) NULL DEFAULT NULL COMMENT '会话标题（可由首条问题生成）',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0 已结束，1 进行中',
  `message_count` INT NOT NULL DEFAULT 0 COMMENT '会话消息条数',
  `last_message_at` DATETIME NULL DEFAULT NULL COMMENT '最近一条消息时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_chat_session_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_chat_session_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_chat_session_kp_id` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_session_no` (`session_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_kp_id` (`kp_id`),
  KEY `idx_scene_type` (`scene_type`),
  KEY `idx_last_message_at` (`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对话会话表';

-- 表19：对话消息表
-- 注：消息为仅追加数据，不做物理修改，故只保留 create_time。
CREATE TABLE `chat_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID，主键',
  `session_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 chat_session.id',
  `sender_role` TINYINT UNSIGNED NOT NULL COMMENT '发送方：1 学生，2 数字人，3 系统',
  `msg_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '消息类型：1 文本，2 语音，3 图片，4 文件',
  `content` TEXT NOT NULL COMMENT '消息文本内容（语音为转写文本）',
  `media_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '语音 / 图片 / 文件 URL',
  `voice_duration` INT NULL DEFAULT NULL COMMENT '语音时长（秒）',
  `cited_sources` JSON NULL DEFAULT NULL COMMENT '引用的知识库来源（文档ID + 页码列表）',
  `guide_stage` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '引导阶段：1 思路，2 步骤，3 提示，4 总结',
  `feedback` TINYINT UNSIGNED NULL DEFAULT 0 COMMENT '学生反馈：0 未评价，1 有用，2 看不懂',
  `reply_duration_ms` INT NULL DEFAULT NULL COMMENT '数字人响应耗时（毫秒）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间（仅追加，不修改）',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_chat_message_session_id` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_session_id` (`session_id`),
  KEY `idx_sender_role` (`sender_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对话消息表';

-- 表20：题库题目表
CREATE TABLE `question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '题目ID，主键',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 course.id',
  `kp_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '主考知识点 knowledge_point.id',
  `question_type` TINYINT UNSIGNED NOT NULL COMMENT '题型：1 单选，2 多选，3 判断，4 填空，5 简答，6 计算，7 编程，8 其他',
  `difficulty` TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '难度，取值 1～5',
  `content` TEXT NOT NULL COMMENT '题干（支持 LaTeX 公式与图片占位）',
  `options` JSON NULL DEFAULT NULL COMMENT '选项内容（选择题使用，以 JSON 存储各选项标号与对应文本）',
  `answer` TEXT NULL DEFAULT NULL COMMENT '标准答案',
  `analysis` TEXT NULL DEFAULT NULL COMMENT '答案解析',
  `source_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '来源：1 真题，2 教材习题，3 教师录入，4 AI 生成',
  `source_desc` VARCHAR(200) NULL DEFAULT NULL COMMENT '来源说明（年份 / 学校 / 章节）',
  `estimated_minutes` INT NULL DEFAULT NULL COMMENT '建议作答用时（分钟）',
  `usage_count` INT NOT NULL DEFAULT 0 COMMENT '被作业 / 练习引用次数',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0 下架，1 正常',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_question_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_question_kp_id` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_course_id` (`course_id`),
  KEY `idx_kp_id` (`kp_id`),
  KEY `idx_question_type` (`question_type`),
  KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题库题目表';

-- 表21：作业表
CREATE TABLE `homework` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '作业ID，主键',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 course.id',
  `class_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '面向班级 class_info.id',
  `teacher_id` BIGINT UNSIGNED NOT NULL COMMENT '发布教师 user_id',
  `title` VARCHAR(100) NOT NULL COMMENT '作业标题',
  `description` TEXT NULL DEFAULT NULL COMMENT '作业说明 / 要求',
  `homework_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '类型：1 普通作业，2 模拟卷',
  `publish_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `deadline` DATETIME NOT NULL COMMENT '提交截止时间',
  `estimated_minutes` INT NULL DEFAULT NULL COMMENT '系统预估完成时长（分钟）',
  `allow_late` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许迟交：0 否，1 是',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0 草稿，1 已发布，2 已截止，3 已归档',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_homework_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_homework_class_id` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_homework_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_course_id` (`course_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_deadline` (`deadline`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作业表';

-- 表22：作业题目关联表
-- 注：联合唯一键 uk_hw_question(homework_id, question_id)，实现作业与题目的多对多关联。
CREATE TABLE `homework_question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联ID，主键',
  `homework_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 homework.id',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 question.id',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '题目在作业中的顺序号',
  `score` DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '本题分值',
  `require_guide` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否强制引导式作答：0 否，1 是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_homework_question_homework_id` FOREIGN KEY (`homework_id`) REFERENCES `homework` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_homework_question_question_id` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_hw_question` (`homework_id`, `question_id`),
  KEY `idx_homework_id` (`homework_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作业题目关联表';

-- 表23：作业提交表
-- 注：联合唯一键 uk_hw_user(homework_id, user_id)，一份作业每名学生仅一条提交记录。
CREATE TABLE `homework_submission` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '提交记录ID，主键',
  `homework_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 homework.id',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `submit_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0 未开始，1 进行中，2 按时提交，3 迟交',
  `start_at` DATETIME NULL DEFAULT NULL COMMENT '开始作答时间',
  `submit_at` DATETIME NULL DEFAULT NULL COMMENT '实际提交时间',
  `used_seconds` INT NULL DEFAULT NULL COMMENT '实际作答用时（秒）',
  `objective_score` DECIMAL(5,1) NULL DEFAULT NULL COMMENT '客观题得分',
  `subjective_score` DECIMAL(5,1) NULL DEFAULT NULL COMMENT '主观题得分',
  `total_score` DECIMAL(5,1) NULL DEFAULT NULL COMMENT '总得分',
  `grade_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '批阅状态：0 待批，1 自动批改完成，2 教师批阅完成',
  `ai_comment` TEXT NULL DEFAULT NULL COMMENT 'AI 评语',
  `teacher_comment` TEXT NULL DEFAULT NULL COMMENT '教师评语',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_homework_submission_homework_id` FOREIGN KEY (`homework_id`) REFERENCES `homework` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_homework_submission_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_hw_user` (`homework_id`, `user_id`),
  KEY `idx_homework_id` (`homework_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_submit_status` (`submit_status`),
  KEY `idx_total_score` (`total_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作业提交表';

-- 表24：作答明细表
-- 注：学情分析的核心事实表，作业、拍照提问、同类题练习、模考等每一次作答均落一条记录。
CREATE TABLE `answer_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '作答记录ID，主键',
  `submission_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联作业提交 homework_submission.id（自由练习为空）',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 question.id',
  `kp_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '冗余知识点ID，便于薄弱点统计',
  `source_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '来源：1 作业，2 拍照提问，3 同类题练习，4 模考，5 抽背默写',
  `user_answer` TEXT NULL DEFAULT NULL COMMENT '学生作答内容',
  `is_correct` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '判定：0 错误，1 正确，2 半对，3 待人工判定',
  `score` DECIMAL(5,1) NULL DEFAULT NULL COMMENT '本题得分',
  `used_seconds` INT NULL DEFAULT NULL COMMENT '作答用时（秒）',
  `guide_rounds` INT NOT NULL DEFAULT 0 COMMENT '引导式交互轮次',
  `answer_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '作答时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_answer_record_submission_id` FOREIGN KEY (`submission_id`) REFERENCES `homework_submission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_answer_record_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_answer_record_question_id` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_answer_record_kp_id` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_submission_id` (`submission_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_kp_id` (`kp_id`),
  KEY `idx_source_type` (`source_type`),
  KEY `idx_is_correct` (`is_correct`),
  KEY `idx_answer_time` (`answer_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作答明细表';

-- 表25：错题本表
-- 注：联合唯一键 uk_user_question(user_id, question_id)，同一道题对一名学生只保留一条错题记录，重复错误累加 wrong_count。
CREATE TABLE `wrong_question` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '错题记录ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `question_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 question.id',
  `kp_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联知识点ID',
  `answer_record_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最近一次作答明细 answer_record.id',
  `wrong_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '错误原因分类（概念不清 / 计算失误等）',
  `wrong_count` INT NOT NULL DEFAULT 1 COMMENT '累计错误次数',
  `mastery_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '掌握状态：0 待掌握，1 复习中，2 已掌握',
  `last_wrong_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次答错时间',
  `resolved_at` DATETIME NULL DEFAULT NULL COMMENT '确认掌握 / 移出错题本时间',
  `guide_record` TEXT NULL DEFAULT NULL COMMENT '引导式讲解过程记录',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_wrong_question_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_wrong_question_question_id` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_wrong_question_kp_id` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_wrong_question_answer_record_id` FOREIGN KEY (`answer_record_id`) REFERENCES `answer_record` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_user_question` (`user_id`, `question_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_kp_id` (`kp_id`),
  KEY `idx_answer_record_id` (`answer_record_id`),
  KEY `idx_mastery_status` (`mastery_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='错题本表';

-- 表26：学习计划表
CREATE TABLE `study_plan` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '计划ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `course_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 course.id',
  `plan_type` TINYINT UNSIGNED NOT NULL COMMENT '类型：1 预习，2 复习，3 备考',
  `title` VARCHAR(100) NOT NULL COMMENT '计划标题',
  `source_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '来源：1 系统生成，2 手动创建',
  `start_date` DATE NOT NULL COMMENT '开始日期',
  `end_date` DATE NULL DEFAULT NULL COMMENT '结束日期',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0 未开始，1 进行中，2 已完成，3 已过期',
  `progress` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '完成进度（0～100）',
  `item_count` INT NOT NULL DEFAULT 0 COMMENT '计划任务项总数',
  `finished_count` INT NOT NULL DEFAULT 0 COMMENT '已完成任务项数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_study_plan_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_study_plan_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_user_id` (`user_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_plan_type` (`plan_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学习计划表';

-- 表27：复习提醒表（记忆曲线）
CREATE TABLE `review_reminder` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '提醒ID，主键',
  `plan_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 study_plan.id',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `kp_id` BIGINT UNSIGNED NOT NULL COMMENT '待复习知识点 knowledge_point.id',
  `stage` TINYINT UNSIGNED NOT NULL COMMENT '记忆曲线阶段：1 当天，2 第1天，3 第2天，4 第4天，5 第7天，6 第15天',
  `remind_at` DATETIME NOT NULL COMMENT '计划提醒时间',
  `remind_content` VARCHAR(200) NULL DEFAULT NULL COMMENT '提醒形式：考点抽背 / 概念默写 / 公式速记',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0 待提醒，1 已完成，2 已跳过',
  `finished_at` DATETIME NULL DEFAULT NULL COMMENT '实际完成时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_review_reminder_plan_id` FOREIGN KEY (`plan_id`) REFERENCES `study_plan` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_review_reminder_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_review_reminder_kp_id` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_kp_id` (`kp_id`),
  KEY `idx_remind_at` (`remind_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='复习提醒表（记忆曲线）';

-- 表28：课件与课堂录音表
CREATE TABLE `courseware` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资料ID，主键',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 course.id',
  `chapter_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联 chapter.id',
  `teacher_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '上传教师 user_id',
  `ware_type` TINYINT UNSIGNED NOT NULL COMMENT '类型：1 课件 PPT，2 教材，3 课堂录音，4 其他',
  `title` VARCHAR(200) NOT NULL COMMENT '资料标题',
  `file_url` VARCHAR(255) NOT NULL COMMENT '课件文件 / 录音音频 URL',
  `duration_seconds` INT NULL DEFAULT NULL COMMENT '录音时长（秒）',
  `parse_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '解析状态：0 待解析，1 解析中，2 成功，3 失败',
  `structure_note` TEXT NULL DEFAULT NULL COMMENT '课件解析生成的结构化笔记',
  `transcript_text` MEDIUMTEXT NULL DEFAULT NULL COMMENT '课堂录音 ASR 转写文本',
  `key_points` TEXT NULL DEFAULT NULL COMMENT 'AI 提炼的课堂重点',
  `class_at` DATETIME NULL DEFAULT NULL COMMENT '上课时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_courseware_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_courseware_chapter_id` FOREIGN KEY (`chapter_id`) REFERENCES `chapter` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_courseware_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_course_id` (`course_id`),
  KEY `idx_chapter_id` (`chapter_id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_ware_type` (`ware_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课件与课堂录音表';

-- 表29：真题 / 模拟卷表
CREATE TABLE `exam_paper` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '试卷ID，主键',
  `course_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 course.id',
  `paper_name` VARCHAR(200) NOT NULL COMMENT '试卷名称',
  `paper_type` TINYINT UNSIGNED NOT NULL COMMENT '类型：1 历年真题，2 模拟卷，3 押题卷',
  `year` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '考试年份',
  `source_desc` VARCHAR(200) NULL DEFAULT NULL COMMENT '来源说明（院校 / 地区）',
  `total_score` DECIMAL(5,1) NOT NULL DEFAULT 100 COMMENT '试卷总分',
  `duration_minutes` INT NOT NULL DEFAULT 120 COMMENT '考试时长（分钟）',
  `file_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '试卷文件 URL',
  `question_ids` JSON NULL DEFAULT NULL COMMENT '组成题目的 question.id 列表（有序）',
  `analysis_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '配套解析材料 URL',
  `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0 下架，1 正常',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_exam_paper_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_course_id` (`course_id`),
  KEY `idx_paper_type` (`paper_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='真题 / 模拟卷表';

-- 表30：每日学习统计表
-- 注：联合唯一键 uk_user_course_date(user_id, course_id, stat_date)，由离线 / 实时统计任务每日聚合生成；course_id 为 0 表示跨课程的通用学习统计。
-- course_id 的非零值应引用 course.id；0 表示跨课程聚合统计。
CREATE TABLE `study_stat_daily` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '统计记录ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `course_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '课程ID，0 表示跨课程通用统计',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `effective_minutes` INT NOT NULL DEFAULT 0 COMMENT '有效学习时长（分钟，剔除挂机）',
  `qa_count` INT NOT NULL DEFAULT 0 COMMENT '当日答疑次数',
  `homework_count` INT NOT NULL DEFAULT 0 COMMENT '当日完成作业数',
  `exercise_count` INT NOT NULL DEFAULT 0 COMMENT '当日练习题数',
  `correct_count` INT NOT NULL DEFAULT 0 COMMENT '当日答对题数',
  `focus_score` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '当日专注度（0～100）',
  `review_finished` INT NOT NULL DEFAULT 0 COMMENT '当日完成复习任务数',
  `avg_answer_seconds` INT NULL DEFAULT NULL COMMENT '平均作答用时（秒）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_study_stat_daily_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_user_course_date` (`user_id`, `course_id`, `stat_date`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='每日学习统计表';

-- 表31：知识点掌握度表
-- 注：联合唯一键 uk_user_kp(user_id, kp_id)，由作答明细按知识点定期聚合计算。
CREATE TABLE `knowledge_mastery` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '掌握度记录ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `kp_id` BIGINT UNSIGNED NOT NULL COMMENT '关联 knowledge_point.id',
  `course_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '冗余课程ID',
  `mastery_score` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '掌握度综合得分（0～100）',
  `accuracy_rate` DECIMAL(5,2) NULL DEFAULT NULL COMMENT '练习正确率（百分比）',
  `avg_answer_seconds` INT NULL DEFAULT NULL COMMENT '平均作答用时（秒）',
  `review_completion` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '复习完成度（0～100）',
  `exercise_total` INT NOT NULL DEFAULT 0 COMMENT '累计练习题数',
  `weak_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否标记为薄弱点：0 否，1 是',
  `last_calc_at` DATETIME NULL DEFAULT NULL COMMENT '最近一次计算时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_knowledge_mastery_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_mastery_kp_id` FOREIGN KEY (`kp_id`) REFERENCES `knowledge_point` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_knowledge_mastery_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_user_kp` (`user_id`, `kp_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_kp_id` (`kp_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_weak_flag` (`weak_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识点掌握度表';

-- 表32：成长报告表
-- 注：联合唯一键 uk_user_period(user_id, report_type, period_start)，每名学生每个周期仅一份报告。
CREATE TABLE `growth_report` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '报告ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '学生 user_id',
  `report_type` TINYINT UNSIGNED NOT NULL COMMENT '报告类型：1 周报，2 月报',
  `period_start` DATE NOT NULL COMMENT '统计周期开始日期',
  `period_end` DATE NOT NULL COMMENT '统计周期结束日期',
  `total_minutes` INT NOT NULL DEFAULT 0 COMMENT '周期内总学习时长（分钟）',
  `radar_data` JSON NOT NULL COMMENT '掌握度雷达图五维得分数据',
  `weak_subjects` JSON NULL DEFAULT NULL COMMENT '薄弱学科 / 薄弱知识点列表',
  `trend_data` JSON NULL DEFAULT NULL COMMENT '进步趋势数据（按日 / 周序列）',
  `diagnosis` TEXT NULL DEFAULT NULL COMMENT '学情诊断结论',
  `suggestions` TEXT NULL DEFAULT NULL COMMENT '个性化提升建议',
  `generate_type` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '生成方式：1 系统生成，2 教师补充',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_growth_report_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  UNIQUE KEY `uk_user_period` (`user_id`, `report_type`, `period_start`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='成长报告表';

-- 表33：通知表
CREATE TABLE `notification` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID，主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '接收人 user_id',
  `biz_type` TINYINT UNSIGNED NOT NULL COMMENT '业务类型：1 作业截止，2 复习提醒，3 疲劳提醒，4 学情预警，5 系统通知',
  `biz_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联业务记录ID（作业ID / 提醒ID等）',
  `title` VARCHAR(100) NOT NULL COMMENT '通知标题',
  `content` VARCHAR(500) NOT NULL COMMENT '通知内容',
  `send_channel` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '发送渠道：1 站内，2 移动推送，3 短信',
  `read_status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '阅读状态：0 未读，1 已读',
  `send_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `read_at` DATETIME NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间（仅追加，不修改）',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_notification_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  KEY `idx_user_id` (`user_id`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_read_status` (`read_status`),
  KEY `idx_send_at` (`send_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知表';

-- ============================================================
-- v1.2 新增（2026-11-09）：登录方式扩展
-- ============================================================

-- 表34：扫码登录白名单表
-- 用途：扫码登录时校验账号是否允许登录（接口 POST /qr/scan）。
-- 也可先用配置文件 qr.login.whitelist 维护，本表供后台可视化维护时启用。
CREATE TABLE `login_whitelist` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '白名单ID，主键',
  `account` VARCHAR(64) NOT NULL COMMENT '白名单账号（对应 sys_user.user_no，如学号 / 工号）',
  `remark` VARCHAR(128) NULL DEFAULT NULL COMMENT '备注，如：罗鸢-学生 / 2024级软件工程',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0 停用，1 启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='扫码登录白名单表';

-- 初始化白名单（按需启用；示例账号与演示环境一致）
-- INSERT IGNORE INTO `login_whitelist` (`account`, `remark`) VALUES
--   ('2024100236', '罗鸢-学生'),
--   ('T20001',     '王老师-教师');

-- 表35：登录审计日志表
-- 用途：手机验证码、扫码、密码三种登录方式统一留痕。
-- 说明：sys_user 仅有 last_login_at / last_login_ip 两个字段，无法满足审计要求；
--       本表为追加型日志，不做物理修改。
CREATE TABLE `auth_login_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键',
  `user_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '登录用户ID（账号不存在或登录失败时可为空）',
  `account` VARCHAR(64) NULL DEFAULT NULL COMMENT '本次提交的登录账号 / 手机号',
  `login_type` TINYINT UNSIGNED NOT NULL COMMENT '登录方式：1 学号密码，2 工号密码，3 手机号验证码，4 扫码登录',
  `login_result` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '登录结果：0 失败，1 成功',
  `fail_reason` VARCHAR(200) NULL DEFAULT NULL COMMENT '失败原因：密码错误 / 验证码错误 / 不在白名单 / 二维码过期 等',
  `device_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '设备唯一标识（对应请求头 X-Device-Id）',
  `ip` VARCHAR(45) NULL DEFAULT NULL COMMENT '登录 IP（兼容 IPv6）',
  `user_agent` VARCHAR(255) NULL DEFAULT NULL COMMENT '客户端 User-Agent',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录发生时间（仅追加，不修改）',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_auth_login_log_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  KEY `idx_user_id` (`user_id`),
  KEY `idx_login_type` (`login_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录审计日志表';

-- 导入后核对：应返回29张表（原27张 + login_whitelist + auth_login_log）。
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'ai_tutor' AND table_type = 'BASE TABLE';

-- ============================================================
-- 附录：已有数据库的升级脚本（新库导入请忽略本节）
-- 说明：本文件主体面向新建数据库；若库已存在，请逐条执行以下语句
--       （MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报错，执行前请先确认）。
-- ------------------------------------------------------------
-- ALTER TABLE `sys_user`
--   MODIFY COLUMN `password_hash` VARCHAR(128) NULL DEFAULT NULL
--     COMMENT '登录密码哈希值（bcrypt 加密，不存明文）；纯手机验证码用户可为空';
--
-- ALTER TABLE `sys_user`
--   ADD COLUMN `phone_verified` TINYINT(1) NOT NULL DEFAULT 0
--     COMMENT '手机号是否已通过验证码校验：0 否，1 是' AFTER `phone`;
--
-- -- 建立唯一索引前先检查是否存在重复手机号，否则会执行失败：
-- -- SELECT `phone`, COUNT(*) c FROM `sys_user`
-- --  WHERE `phone` IS NOT NULL AND `is_deleted` = 0 GROUP BY `phone` HAVING c > 1;
-- ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_phone` (`phone`, `is_deleted`);
--
-- -- 新增两张表（直接执行上面的 CREATE TABLE 即可，无外键冲突）
-- ============================================================
