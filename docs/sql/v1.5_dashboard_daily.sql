-- ============================================================
-- 数据大屏当日数据补齐（v1.5）
-- 用途：演示数据按天生成，跨天后“今日”指标会归零，执行本脚本补齐当天数据
-- 特性：幂等，可每天重复执行，不会产生重复数据
-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 < v1.5_dashboard_daily.sql
-- ============================================================
USE `ai_tutor`;

-- 1. 当天学习统计（唯一键 uk_user_course_date 保证幂等）
INSERT IGNORE INTO `study_stat_daily`
  (`user_id`, `course_id`, `stat_date`, `effective_minutes`, `qa_count`, `homework_count`,
   `exercise_count`, `correct_count`, `focus_score`, `review_finished`, `avg_answer_seconds`)
SELECT u.`id`, c.`id`, CURDATE(),
       30 + ((u.`id` * 13 + c.`id` * 7 + DAY(CURDATE()) * 11) % 95),
       (u.`id` + c.`id` + DAY(CURDATE())) % 5,
       (u.`id` + DAY(CURDATE())) % 2,
       15,
       8 + ((u.`id` * 5 + c.`id` * 3 + DAY(CURDATE())) % 7),
       58 + ((u.`id` + DAY(CURDATE()) * 3) % 38),
       (u.`id` + DAY(CURDATE())) % 3,
       40 + ((u.`id` + DAY(CURDATE()) * 2) % 70)
FROM `sys_user` u
CROSS JOIN `course` c
WHERE u.`user_no` LIKE 'S2024%' AND c.`course_code` IN ('CS101', 'EN201', 'MA301');

-- 2. 当天 AI 调用日志（当天已有记录的用户跳过）
INSERT INTO `ai_usage_log`
  (`user_id`, `model_name`, `scene_type`, `prompt_tokens`, `completion_tokens`, `total_tokens`,
   `duration_ms`, `status`, `create_time`)
SELECT u.`id`, 'deepseek-v4-flash',
       1 + (u.`id` % 5),
       280 + ((u.`id` * 7) % 520),
       140 + ((u.`id` * 11) % 420),
       420 + ((u.`id` * 7) % 520) + ((u.`id` * 11) % 420),
       700 + ((u.`id` * 13) % 2300),
       1,
       NOW()
FROM `sys_user` u
WHERE u.`user_no` LIKE 'S2024%'
  AND NOT EXISTS (
    SELECT 1 FROM `ai_usage_log` a
    WHERE a.`user_id` = u.`id` AND a.`create_time` >= CURDATE()
  );

-- 3. 当天答疑会话（会话号带日期，天然幂等）
INSERT INTO `chat_session`
  (`session_no`, `user_id`, `course_id`, `scene_type`, `title`, `status`, `message_count`,
   `last_message_at`, `create_time`)
SELECT CONCAT('DEMO-T-', u.`id`, '-', DATE_FORMAT(CURDATE(), '%Y%m%d')),
       u.`id`, c.`id`, 1 + (u.`id` % 5), '关于知识点的答疑', 0, 0, NOW(), NOW()
FROM `sys_user` u
JOIN `course` c ON c.`course_code` = 'CS101'
WHERE u.`user_no` LIKE 'S2024%'
  AND NOT EXISTS (
    SELECT 1 FROM `chat_session` s
    WHERE s.`session_no` = CONCAT('DEMO-T-', u.`id`, '-', DATE_FORMAT(CURDATE(), '%Y%m%d'))
  );

-- 4. 当天作答明细（大屏不直接使用，供后续扩展）
INSERT INTO `answer_record`
  (`user_id`, `question_id`, `kp_id`, `source_type`, `is_correct`, `score`, `used_seconds`,
   `answer_time`, `create_time`)
SELECT u.`id`, q.`id`, q.`kp_id`, 1,
       IF((u.`id` + q.`id`) % 3 = 0, 0, 1),
       IF((u.`id` + q.`id`) % 3 = 0, 0, 10),
       30 + ((u.`id` * 7 + q.`id` * 3) % 150),
       NOW(), NOW()
FROM `sys_user` u
JOIN (SELECT `id`, `kp_id` FROM `question` WHERE `id` % 8 = 0) q
WHERE u.`user_no` LIKE 'S2024%'
  AND NOT EXISTS (
    SELECT 1 FROM `answer_record` r
    WHERE r.`user_id` = u.`id` AND r.`answer_time` >= CURDATE()
  );

-- 校验输出
SELECT DATE(CURDATE()) AS `date`,
       (SELECT COUNT(*) FROM `study_stat_daily` WHERE `stat_date` = CURDATE()) AS `stat_rows`,
       (SELECT COUNT(*) FROM `ai_usage_log` WHERE `create_time` >= CURDATE()) AS `ai_rows`,
       (SELECT COUNT(*) FROM `chat_session` WHERE `create_time` >= CURDATE()) AS `session_rows`;
