-- ============================================================
-- 私信功能演示数据
-- ============================================================
-- 用途：建立“班级—课程—学生成员”关系，使私信联系人列表可正常展示。
-- 特点：可重复执行，不会重复插入成员。
-- 执行：
--   docker exec -i aitutor-mysql mysql -uroot -p123456 --default-character-set=utf8mb4 ai_tutor < docs/sql/private_chat_demo_data.sql
-- ============================================================

SET NAMES utf8mb4;
USE `ai_tutor`;

START TRANSACTION;

-- 1. 创建演示班级
INSERT INTO `class_info` (
    `class_name`, `class_code`, `college`, `grade`,
    `owner_teacher_id`, `status`, `is_deleted`
) VALUES (
    '软件工程演示班', 'DEMO-SE-2024', '软件学院', '2024',
    8, 1, 0
)
ON DUPLICATE KEY UPDATE
    `class_name` = VALUES(`class_name`),
    `college` = VALUES(`college`),
    `grade` = VALUES(`grade`),
    `owner_teacher_id` = VALUES(`owner_teacher_id`),
    `status` = 1,
    `is_deleted` = 0;

-- 2. 定位演示班级
SELECT @class_id := `id`
FROM `class_info`
WHERE `class_code` = 'DEMO-SE-2024';

-- 3. 将现有四门课程关联到演示班级
UPDATE `course`
SET `class_id` = @class_id,
    `status` = 1,
    `is_deleted` = 0
WHERE `id` IN (1, 2, 3, 4);

-- 4. 将现有学生加入演示班级
INSERT INTO `class_member` (
    `class_id`, `user_id`, `member_type`, `joined_at`
)
SELECT @class_id, `id`, 1, NOW()
FROM `sys_user`
WHERE `id` IN (1, 2, 5, 7)
  AND `status` = 1
  AND `is_deleted` = 0
ON DUPLICATE KEY UPDATE
    `is_deleted` = 0;

COMMIT;

-- 5. 验证数据
SELECT `id`, `class_name`, `class_code`, `owner_teacher_id`, `status`, `is_deleted`
FROM `class_info`
WHERE `class_code` = 'DEMO-SE-2024';

SELECT `id`, `course_code`, `course_name`, `class_id`, `teacher_id`, `status`, `is_deleted`
FROM `course`
WHERE `id` IN (1, 2, 3, 4)
ORDER BY `id`;

SELECT cm.`class_id`, cm.`user_id`, u.`user_no`, u.`real_name`,
       cm.`member_type`, cm.`is_deleted`
FROM `class_member` cm
JOIN `sys_user` u ON u.`id` = cm.`user_id`
WHERE cm.`class_id` = @class_id
ORDER BY cm.`member_type`, cm.`user_id`;
