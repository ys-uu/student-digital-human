-- ============================================================
-- AI助教系统 v1.7 演示数据：WebSocket 1对1实时对话（私信）
-- ============================================================
-- 版本：v1.7（2026-11-21）
-- 依赖：ai_tutor.sql、rbac.sql、v1.3_rag_increment.sql、v1.7_private_message.sql 已执行
-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 < v1.7_private_demo.sql
--
-- 用途：
--   既有种子数据（v1.4）只创建了学生成员的 class_member 记录，
--   没有任何教师被加入班级，导致"同老师 + 同课程"的师生私信关系一条也建立不起来。
--   本脚本补充教师成员记录，使师生私信可演示。
--
-- 变更清单（仅追加，不修改任何既有数据）：
--   1) class_member：补充 2 条教师成员记录
--        · 李老师(id=1) → 软件工程2402(class_id=2)，对应课程 EN201 大学英语
--        · 赵老师(id=2) → 计算机科学2401(class_id=3)，对应课程 MA301 高等数学
--
-- 明确不做的事：
--   · 不修改 course.teacher_id（CS101 数据结构仍为 NULL，该课程本期无师生私信）
--   · 不修改任何 sys_user.role（teacher 账号仍保持原角色）
--   · 不删除、不更新任何既有行
--
-- 幂等说明：
--   class_member 已有唯一键 uk_class_user(class_id, user_id, member_type)，
--   配合 INSERT IGNORE 可重复执行，重复执行不会报错也不会产生重复数据。
--
-- 执行后预期私信关系：
--   学生 ↔ 学生：同班级学生之间可互发（按课程口径，同班即同课程）
--   学生 ↔ 教师：
--     · 吴佳宁/郑一鸣/孙悦然/黄思远  ↔ 李老师（EN201 大学英语）
--     · 刘书瑶/张子墨/徐若彤/何嘉树  ↔ 赵老师（MA301 高等数学）
--     · 陈子昂/林清越/周雨桐/许明轩  ↔ 无对应教师（CS101 未配置 teacher_id）
-- ============================================================
SET NAMES utf8mb4;
USE `ai_tutor`;

-- ------------------------------------------------------------
-- 1. 补充教师班级成员记录
--    member_type = 2 表示任课教师（1 学生 / 2 任课教师 / 3 助教）
--    使用 SELECT ... WHERE EXISTS 方式，保证用户与班级存在时才插入，
--    避免因种子数据缺失导致外键报错。
-- ------------------------------------------------------------

-- 1.1 李老师(id=1) 加入 软件工程2402(class_id=2) —— 对应 EN201 大学英语
INSERT IGNORE INTO `class_member` (`class_id`, `user_id`, `member_type`, `joined_at`)
SELECT 2, 1, 2, NOW()
FROM DUAL
WHERE EXISTS (SELECT 1 FROM `sys_user` WHERE `id` = 1 AND `is_deleted` = 0 AND `role` = 2)
  AND EXISTS (SELECT 1 FROM `class_info` WHERE `id` = 2 AND `is_deleted` = 0);

-- 1.2 赵老师(id=2) 加入 计算机科学2401(class_id=3) —— 对应 MA301 高等数学
INSERT IGNORE INTO `class_member` (`class_id`, `user_id`, `member_type`, `joined_at`)
SELECT 3, 2, 2, NOW()
FROM DUAL
WHERE EXISTS (SELECT 1 FROM `sys_user` WHERE `id` = 2 AND `is_deleted` = 0 AND `role` = 2)
  AND EXISTS (SELECT 1 FROM `class_info` WHERE `id` = 3 AND `is_deleted` = 0);

-- ============================================================
-- 验证语句（执行后手工核对）
-- ============================================================

-- 2.1 各班级的师生构成
SELECT
    ci.`id` AS 班级ID,
    ci.`class_code` AS 班级编码,
    ci.`class_name` AS 班级名称,
    GROUP_CONCAT(DISTINCT CASE WHEN cm.`member_type` = 1 THEN u.`real_name` END SEPARATOR ', ') AS 学生,
    GROUP_CONCAT(DISTINCT CASE WHEN cm.`member_type` IN (2, 3) THEN u.`real_name` END SEPARATOR ', ') AS 教师
FROM `class_info` ci
LEFT JOIN `class_member` cm ON cm.`class_id` = ci.`id` AND cm.`is_deleted` = 0
LEFT JOIN `sys_user` u ON u.`id` = cm.`user_id` AND u.`is_deleted` = 0
WHERE ci.`is_deleted` = 0
GROUP BY ci.`id`, ci.`class_code`, ci.`class_name`
ORDER BY ci.`id`;

-- 2.2 可用的师生私信关系（按"同老师 + 同课程"口径推导）
--     条件：course.teacher_id 非空；学生在 course.class_id 对应班级中；
--           且该教师也在同一班级（member_type in 2,3）。
SELECT
    c.`id` AS 课程ID,
    c.`course_code` AS 课程编码,
    c.`course_name` AS 课程名称,
    t.`real_name` AS 教师,
    s.`real_name` AS 学生
FROM `course` c
JOIN `sys_user` t ON t.`id` = c.`teacher_id` AND t.`is_deleted` = 0
JOIN `class_member` cmt ON cmt.`class_id` = c.`class_id`
                       AND cmt.`user_id` = c.`teacher_id`
                       AND cmt.`member_type` IN (2, 3)
                       AND cmt.`is_deleted` = 0
JOIN `class_member` cms ON cms.`class_id` = c.`class_id`
                       AND cms.`member_type` = 1
                       AND cms.`is_deleted` = 0
JOIN `sys_user` s ON s.`id` = cms.`user_id` AND s.`is_deleted` = 0
WHERE c.`is_deleted` = 0
  AND c.`status` = 1
ORDER BY c.`id`, s.`id`;

-- ============================================================
-- 附：本次明确不处理的数据问题（留待后续按需修正）
-- ============================================================
-- 1) course.id = 1（CS101 数据结构）的 teacher_id 为 NULL，
--    该课程没有任何师生私信关系；如需启用，另行执行：
--      UPDATE course SET teacher_id = 1 WHERE id = 1;
-- 2) sys_user.user_no = 'teacher'（id=16）的 role 为 1（学生），
--    该账号登录后进入学生端；如需作为教师账号使用，另行执行：
--      UPDATE sys_user SET role = 2 WHERE user_no = 'teacher';
-- 3) 上述两条均会修改既有数据，本期按要求不执行。
-- ============================================================