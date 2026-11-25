-- ============================================================
-- v1.9 MongoDB 迁移：消息表改存文档数据库
-- ============================================================
-- 版本：v1.9（2026-11-25）
-- 依赖：v1.8_message_seq.sql、v1.8.1_seq_guard.sql 已执行
--
-- 背景：
--   私信消息需要支持文本、图片、文件、音频四种类型，字段差异大
--   （文本只有 content；图片要宽高；音频要时长；文件要文件名大小）。
--   若继续用 MySQL 关系表，需要不断加可为空的列并 ALTER TABLE，
--   且消息量随时间线性增长、单表膨胀后需要分库分表。
--   因此消息本体迁移到 MongoDB（文档型数据库，天然适配异构结构），
--   强关系数据（账号、课程、班级、会话摘要）仍留 MySQL。
--
-- 本脚本做两件事：
--   1) 新建全局消息 ID 序列表 message_id_seq
--      —— MongoDB 无自增主键，必须用一个集中式计数器保证 id 全局单调递增，
--         否则离线补拉（按 id 游标跨会话判新旧）无法工作；
--   2) 从 MySQL 现有消息表回填序列初值，保证迁移后新 id 不与历史 id 冲突。
--
-- 注意：本脚本只建序列表并回填初值，不删除 private_message 表。
--       存量消息由应用层迁移脚本（MongoSeedRunner）搬运，
--       确认迁移无误后再手动 DROP 旧表。
--
-- 执行说明：可重复执行（用了 IF NOT EXISTS 与幂等 UPDATE）。
-- ============================================================
SET NAMES utf8mb4;
USE `ai_tutor`;

-- ------------------------------------------------------------
-- 1. 全局消息 ID 序列表
-- ------------------------------------------------------------
-- 设计要点：
--   · 单行表，主键恒为 1，current_id 保存"已分配到的最大 id"；
--   · 分配时执行 UPDATE ... SET current_id = LAST_INSERT_ID(current_id + 1)，
--     在 InnoDB 行锁保护下原子自增，并发安全；
--   · 之所以不用 SELECT MAX(id)+1，是因为该写法存在经典竞态，
--     并发时多个请求会拿到同一个 id。
CREATE TABLE IF NOT EXISTS `message_id_seq` (
  `id`         TINYINT UNSIGNED NOT NULL COMMENT '固定为 1，保证单行',
  `current_id` BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '已分配到的最大消息ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='全局消息ID序列（供 MongoDB 消息主键使用）';

-- ------------------------------------------------------------
-- 2. 初始化序列表（幂等）
-- ------------------------------------------------------------
INSERT IGNORE INTO `message_id_seq` (`id`, `current_id`) VALUES (1, 0);

-- ------------------------------------------------------------
-- 3. 用 MySQL 现有消息的最大 id 回填，避免迁移后 id 冲突
--    若 private_message 表不存在（已删），此语句会报错但不影响后续；
--    可改为手动指定：UPDATE message_id_seq SET current_id = <已知最大值> WHERE id = 1;
-- ------------------------------------------------------------
UPDATE `message_id_seq`
SET `current_id` = GREATEST(
        `current_id`,
        COALESCE((SELECT MAX(`id`) FROM `private_message`), 0)
    )
WHERE `id` = 1;

-- ============================================================
-- 验证
-- ============================================================
-- 1) 序列表存在且有初始值
SELECT `id`, `current_id` AS 当前已分配到, `update_time`
FROM `message_id_seq`;

-- 2) 与旧表最大 id 对比（应相等，表示新消息会从 max+1 开始）
SELECT
    (SELECT COALESCE(MAX(`id`), 0) FROM `private_message`)        AS 旧表最大id,
    (SELECT `current_id` FROM `message_id_seq` WHERE `id` = 1)    AS 序列当前值;

-- 3) 测试分配（可选，执行后 current_id 会 +1）
-- UPDATE message_id_seq SET current_id = LAST_INSERT_ID(current_id + 1) WHERE id = 1;
-- SELECT LAST_INSERT_ID() AS 分配到的id;