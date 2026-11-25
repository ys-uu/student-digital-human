-- ============================================================
-- AI助教系统 v1.8.1 增量脚本：seq 完整性防护
-- ============================================================
-- 版本：v1.8.1（2026-11-22）
-- 依赖：v1.8_message_seq.sql 已执行
--
-- 背景（真实踩坑记录）：
--   v1.8 把 seq 定义为 BIGINT UNSIGNED NOT NULL 且无默认值。
--   但旧版后端代码的 INSERT 语句尚未包含 seq 列，MySQL 在非严格列匹配下
--   会为该列填充隐式默认值 0 —— 于是产生了 seq = 0 的非法数据，
--   导致会话内排序错乱（0 排在 1 之前），且客户端空洞检测失效。
--
-- 本脚本的作用：
--   1) 加 CHECK 约束 seq >= 1，让任何漏填 seq 的写入直接报错而非静默写入 0，
--      把"静默错误"变成"显式失败"，便于第一时间发现代码与表结构不一致；
--   2) 重建 CHECK 时顺带校验存量数据，若仍有 seq=0 会直接失败，提示先清洗。
--
-- 为什么不用 DEFAULT 1：
--   给 seq 设默认值会让并发写入全部撞 uk_conv_seq(conversation_id, seq)，
--   报错更难排查；显式约束更符合"seq 必须由服务端发号"的语义。
--
-- 执行说明：不可重复执行（重复添加同名 CHECK 会报错）。
-- ============================================================
SET NAMES utf8mb4;
USE `ai_tutor`;

-- ------------------------------------------------------------
-- 1. 前置校验：确保存量数据没有 seq = 0 或 NULL
--    若下方查询返回非空结果，请先清洗数据再执行本脚本。
-- ------------------------------------------------------------
SELECT '存在非法 seq 数据，请先清洗' AS warning, id, conversation_id, seq
FROM `private_message`
WHERE `seq` IS NULL OR `seq` < 1;

-- ------------------------------------------------------------
-- 2. 添加 CHECK 约束：seq 必须 >= 1
-- ------------------------------------------------------------
ALTER TABLE `private_message`
  ADD CONSTRAINT `chk_seq_positive` CHECK (`seq` >= 1);

-- ============================================================
-- 验证
-- ============================================================
-- 1) 约束存在
SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'ai_tutor' AND CONSTRAINT_NAME = 'chk_seq_positive';

-- 2) 尝试插入 seq=0 应被拒绝（预期报错 Check constraint violated，属正常）
-- INSERT INTO private_message
--   (conversation_id, seq, sender_id, receiver_id, content, msg_type, client_msg_id)
--   VALUES (1, 0, 7, 1, 'should-fail', 1, 'chk-test');