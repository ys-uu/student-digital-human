-- ============================================================
-- AI助教系统 v1.8 增量脚本：会话级消息序号 seq
-- ============================================================
-- 版本：v1.8（2026-11-21）
-- 依赖：v1.7_private_message.sql 已执行
-- 依据：《实时聊天系统技术与实现》P11「消息顺序性：会话内 seq 单调递增」
--       与 P19「一切消息要有序号：seq 是顺序、去重、离线同步、未读数的公共地基」
--
-- 为什么不能用自增主键 id 当序号：
--   1) id 是全局自增，跨会话穿插。A 会话 1~100、B 会话 150 时，
--      客户端按 seq === localMax + 1 做空洞检测永远不成立；
--   2) 分库分表后全局自增 id 无法保证会话内连续；
--   3) 客户端时钟不可信，也不能用 create_time 排序（NTP 漂移、人为改表）。
--
-- 变更清单：
--   一、ALTER private_message
--     1) 新增 seq BIGINT UNSIGNED NOT NULL —— 会话内单调递增序号，由 Redis INCR 发号；
--     2) 新增唯一键 uk_conv_seq(conversation_id, seq) —— 保证同一会话内序号不重复；
--     3) 新增索引 idx_conv_seq(conversation_id, seq) —— 支撑按 seq 游标增量拉取；
--     4) 新增 delivered_seq —— 对端已送达到的序号（用于"已送达"状态回执）。
--   二、数据回填
--     按 (conversation_id, create_time, id) 顺序为存量消息补发 seq，
--     使旧数据也满足"会话内从 1 开始连续递增"。
--   三、初始化 Redis 发号器基线
--     由应用层首次发号时自动从 max(seq) 起算，无需手工写 Redis。
--
-- 执行说明：
--   · ALTER 语句不可重复执行（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS），
--     重复执行会报"Duplicate column"错误，属预期行为；
--   · 本脚本对空表与有数据表均安全。
-- ============================================================
SET NAMES utf8mb4;
USE `ai_tutor`;

-- ------------------------------------------------------------
-- 1. 新增 seq 列
--    先加为可空，回填后再改为 NOT NULL，避免已有行因无默认值报错。
-- ------------------------------------------------------------
ALTER TABLE `private_message`
  ADD COLUMN `seq` BIGINT UNSIGNED NULL DEFAULT NULL
    COMMENT '会话内单调递增序号，由 Redis INCR 发号，从 1 开始连续' AFTER `conversation_id`;

-- ------------------------------------------------------------
-- 2. 存量数据回填
--    同一会话内按 create_time、id 排序，用 MySQL 8.0 窗口函数生成行号。
-- ------------------------------------------------------------
UPDATE `private_message` pm
JOIN (
  SELECT `id`,
         ROW_NUMBER() OVER (PARTITION BY `conversation_id` ORDER BY `create_time`, `id`) AS rn
  FROM `private_message`
) t ON t.`id` = pm.`id`
SET pm.`seq` = t.rn;

-- ------------------------------------------------------------
-- 3. 回填完成后收紧为 NOT NULL
-- ------------------------------------------------------------
ALTER TABLE `private_message`
  MODIFY COLUMN `seq` BIGINT UNSIGNED NOT NULL
    COMMENT '会话内单调递增序号，由 Redis INCR 发号，从 1 开始连续';

-- ------------------------------------------------------------
-- 4. 索引：同会话 seq 唯一 + 按 seq 增量拉取
-- ------------------------------------------------------------
ALTER TABLE `private_message`
  ADD UNIQUE KEY `uk_conv_seq` (`conversation_id`, `seq`),
  ADD KEY `idx_conv_seq` (`conversation_id`, `seq`);

-- ------------------------------------------------------------
-- 5. 已送达序号
--    记录「接收方已确认送达到的最大 seq」，用于三态中的"已送达"。
--    语义与 delivered_at 互补：delivered_at 是时间点，delivered_seq 是进度。
-- ------------------------------------------------------------
ALTER TABLE `private_conversation`
  ADD COLUMN `a_delivered_seq` BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT 'A 已送达到的最大 seq' AFTER `a_last_read_id`,
  ADD COLUMN `b_delivered_seq` BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT 'B 已送达到的最大 seq' AFTER `b_last_read_id`;

-- ============================================================
-- 验证语句
-- ============================================================
-- 1) 每个会话的 seq 应从 1 开始连续，max(seq) 与 count(*) 相等
SELECT `conversation_id`,
       COUNT(*) AS 消息数,
       MIN(`seq`) AS 最小seq,
       MAX(`seq`) AS 最大seq,
       MAX(`seq`) - MIN(`seq`) + 1 = COUNT(*) AS 是否连续
FROM `private_message`
GROUP BY `conversation_id`;

-- 2) 索引确认
SHOW INDEX FROM `private_message` WHERE Key_name IN ('uk_conv_seq', 'idx_conv_seq');

-- 3) 会话表新增列确认
SHOW COLUMNS FROM `private_conversation` LIKE '%delivered_seq';