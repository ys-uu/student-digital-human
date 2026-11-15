-- ============================================================
-- 数据大屏演示数据（v1.4）
-- 依赖：ai_tutor.sql、rbac.sql、v1.3_rag_increment.sql 已执行
-- 说明：本脚本仅用于演示，可重复执行（第二次执行自动跳过）
--       生成 3 个班级、3 门课程、12 名学生、近 30 天学习与 AI 调用数据
-- 执行：mysql -uroot -p123456 --default-character-set=utf8mb4 < v1.4_dashboard_demo.sql
-- ============================================================
USE `ai_tutor`;

CREATE TABLE IF NOT EXISTS `demo_seed_log` (
  `seed_key` VARCHAR(64) NOT NULL COMMENT '种子标识',
  `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`seed_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示数据执行记录';

DROP PROCEDURE IF EXISTS `seed_dashboard_demo`;

DELIMITER $$

CREATE PROCEDURE `seed_dashboard_demo`()
BEGIN
  DECLARE v_pwd VARCHAR(72) DEFAULT '$2a$10$SZZlrKtk9VyLU5vDB5/Xt.MyfDlL.6kn.db6kpoi/LBLnNYft7qEC';
  DECLARE v_t1 BIGINT DEFAULT 0;
  DECLARE v_t2 BIGINT DEFAULT 0;
  DECLARE v_t3 BIGINT DEFAULT 0;
  DECLARE v_c1 BIGINT DEFAULT 0;
  DECLARE v_c2 BIGINT DEFAULT 0;
  DECLARE v_c3 BIGINT DEFAULT 0;
  DECLARE v_ch BIGINT DEFAULT 0;
  DECLARE v_cls1 BIGINT DEFAULT 0;
  DECLARE v_cls2 BIGINT DEFAULT 0;
  DECLARE v_cls3 BIGINT DEFAULT 0;
  DECLARE v_d INT DEFAULT 0;
  DECLARE v_date DATE;
  DECLARE v_uid BIGINT DEFAULT 0;

  IF EXISTS (SELECT 1 FROM `demo_seed_log` WHERE `seed_key` = 'dashboard_v1') THEN
    SELECT '演示数据已存在，跳过' AS message;
  ELSE
    -- ---------- 教师 ----------
    INSERT INTO `sys_user` (`user_no`, `password_hash`, `real_name`, `role`, `status`)
    VALUES ('T20002', v_pwd, '李老师', 2, 1), ('T20003', v_pwd, '赵老师', 2, 1);
    SET v_t1 = (SELECT `id` FROM `sys_user` WHERE `user_no` = 'T20001');
    SET v_t2 = (SELECT `id` FROM `sys_user` WHERE `user_no` = 'T20002');
    SET v_t3 = (SELECT `id` FROM `sys_user` WHERE `user_no` = 'T20003');
    INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES (v_t2, 2), (v_t3, 2);
    INSERT INTO `teacher_profile` (`user_id`, `teacher_no`, `college`, `department`, `title`)
    VALUES (v_t2, 'T20002', '计算机学院', '英语教研室', '讲师'),
           (v_t3, 'T20003', '数学学院', '高等数学教研室', '副教授');

    -- ---------- 班级 ----------
    INSERT INTO `class_info` (`class_name`, `class_code`, `college`, `grade`, `owner_teacher_id`, `status`)
    VALUES ('软件工程2401', 'SE2401', '计算机学院', '2024', v_t1, 1),
           ('软件工程2402', 'SE2402', '计算机学院', '2024', v_t2, 1),
           ('计算机科学2401', 'CS2401', '计算机学院', '2024', v_t3, 1);
    SET v_cls1 = (SELECT `id` FROM `class_info` WHERE `class_code` = 'SE2401');
    SET v_cls2 = (SELECT `id` FROM `class_info` WHERE `class_code` = 'SE2402');
    SET v_cls3 = (SELECT `id` FROM `class_info` WHERE `class_code` = 'CS2401');

    -- ---------- 课程 ----------
    INSERT INTO `course` (`course_code`, `course_name`, `college`, `credit`, `course_category`, `subject_type`, `class_id`, `teacher_id`, `intro`, `status`)
    VALUES ('CS101', '数据结构', '计算机学院', 4.0, 2, 1, v_cls1, v_t1, '线性表、树、图与常用算法', 1),
           ('EN201', '大学英语', '外国语学院', 3.0, 1, 2, v_cls2, v_t2, '综合英语能力训练', 1),
           ('MA301', '高等数学', '数学学院', 5.0, 1, 1, v_cls3, v_t3, '极限、导数、积分与级数', 1);
    SET v_c1 = (SELECT `id` FROM `course` WHERE `course_code` = 'CS101');
    SET v_c2 = (SELECT `id` FROM `course` WHERE `course_code` = 'EN201');
    SET v_c3 = (SELECT `id` FROM `course` WHERE `course_code` = 'MA301');

    -- ---------- 学生 ----------
    INSERT INTO `sys_user` (`user_no`, `password_hash`, `real_name`, `role`, `status`) VALUES
      ('S2024001', v_pwd, '陈子昂', 1, 1), ('S2024002', v_pwd, '林清越', 1, 1),
      ('S2024003', v_pwd, '周雨桐', 1, 1), ('S2024004', v_pwd, '许明轩', 1, 1),
      ('S2024005', v_pwd, '吴佳宁', 1, 1), ('S2024006', v_pwd, '郑一鸣', 1, 1),
      ('S2024007', v_pwd, '孙悦然', 1, 1), ('S2024008', v_pwd, '黄思远', 1, 1),
      ('S2024009', v_pwd, '刘书瑶', 1, 1), ('S2024010', v_pwd, '张子墨', 1, 1),
      ('S2024011', v_pwd, '徐若彤', 1, 1), ('S2024012', v_pwd, '何嘉树', 1, 1);

    INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
    SELECT `id`, 1 FROM `sys_user` WHERE `user_no` LIKE 'S2024%';

    INSERT INTO `student_profile` (`user_id`, `student_no`, `college`, `major`, `grade`, `class_name`, `enrollment_year`)
    SELECT u.`id`, u.`user_no`, '计算机学院',
           CASE WHEN u.`user_no` <= 'S2024004' THEN '软件工程'
                WHEN u.`user_no` <= 'S2024008' THEN '软件工程'
                ELSE '计算机科学与技术' END,
           '2024',
           CASE WHEN u.`user_no` <= 'S2024004' THEN '软件工程2401'
                WHEN u.`user_no` <= 'S2024008' THEN '软件工程2402'
                ELSE '计算机科学2401' END,
           2024
    FROM `sys_user` u WHERE u.`user_no` LIKE 'S2024%';

    INSERT INTO `class_member` (`class_id`, `user_id`, `member_type`)
    SELECT CASE WHEN u.`user_no` <= 'S2024004' THEN v_cls1
                WHEN u.`user_no` <= 'S2024008' THEN v_cls2
                ELSE v_cls3 END,
           u.`id`, 1
    FROM `sys_user` u WHERE u.`user_no` LIKE 'S2024%';

    -- ---------- 章节 ----------
    INSERT INTO `chapter` (`course_id`, `chapter_no`, `chapter_name`, `sort_order`, `suggest_minutes`) VALUES
      (v_c1, '1', '线性结构', 1, 180), (v_c1, '2', '树与图', 2, 240),
      (v_c2, '1', '阅读理解', 1, 150), (v_c2, '2', '写作与翻译', 2, 150),
      (v_c3, '1', '极限与导数', 1, 200), (v_c3, '2', '积分与级数', 2, 220);

    SET v_ch = (SELECT `id` FROM `chapter` WHERE `course_id` = v_c1 AND `chapter_no` = '1');
    INSERT INTO `knowledge_point` (`course_id`, `chapter_id`, `kp_code`, `kp_name`, `is_key`, `is_hard`, `mastery_threshold`) VALUES
      (v_c1, v_ch, 'CS101-01', '顺序表与链表', 1, 0, 60),
      (v_c1, v_ch, 'CS101-02', '栈与队列', 1, 0, 60),
      (v_c1, v_ch, 'CS101-03', '串与数组', 0, 0, 60),
      (v_c1, v_ch, 'CS101-04', '递归与分治', 1, 1, 65);
    SET v_ch = (SELECT `id` FROM `chapter` WHERE `course_id` = v_c1 AND `chapter_no` = '2');
    INSERT INTO `knowledge_point` (`course_id`, `chapter_id`, `kp_code`, `kp_name`, `is_key`, `is_hard`, `mastery_threshold`) VALUES
      (v_c1, v_ch, 'CS101-05', '二叉树遍历', 1, 1, 65),
      (v_c1, v_ch, 'CS101-06', '图的存储结构', 1, 1, 65),
      (v_c1, v_ch, 'CS101-07', '最短路径算法', 1, 1, 70),
      (v_c1, v_ch, 'CS101-08', '拓扑排序', 0, 0, 60);
    SET v_ch = (SELECT `id` FROM `chapter` WHERE `course_id` = v_c2 AND `chapter_no` = '1');
    INSERT INTO `knowledge_point` (`course_id`, `chapter_id`, `kp_code`, `kp_name`, `is_key`, `is_hard`, `mastery_threshold`) VALUES
      (v_c2, v_ch, 'EN201-01', '主旨大意题', 1, 0, 60),
      (v_c2, v_ch, 'EN201-02', '细节理解题', 1, 0, 60),
      (v_c2, v_ch, 'EN201-03', '词义猜测题', 0, 1, 60),
      (v_c2, v_ch, 'EN201-04', '推理判断题', 1, 1, 65);
    SET v_ch = (SELECT `id` FROM `chapter` WHERE `course_id` = v_c2 AND `chapter_no` = '2');
    INSERT INTO `knowledge_point` (`course_id`, `chapter_id`, `kp_code`, `kp_name`, `is_key`, `is_hard`, `mastery_threshold`) VALUES
      (v_c2, v_ch, 'EN201-05', '应用文写作', 1, 0, 60),
      (v_c2, v_ch, 'EN201-06', '长难句翻译', 1, 1, 65),
      (v_c2, v_ch, 'EN201-07', '篇章衔接', 0, 0, 60),
      (v_c2, v_ch, 'EN201-08', '学术表达', 0, 1, 65);
    SET v_ch = (SELECT `id` FROM `chapter` WHERE `course_id` = v_c3 AND `chapter_no` = '1');
    INSERT INTO `knowledge_point` (`course_id`, `chapter_id`, `kp_code`, `kp_name`, `is_key`, `is_hard`, `mastery_threshold`) VALUES
      (v_c3, v_ch, 'MA301-01', '极限的计算', 1, 0, 60),
      (v_c3, v_ch, 'MA301-02', '连续性判定', 1, 0, 60),
      (v_c3, v_ch, 'MA301-03', '导数公式', 1, 0, 60),
      (v_c3, v_ch, 'MA301-04', '洛必达法则', 1, 1, 65);
    SET v_ch = (SELECT `id` FROM `chapter` WHERE `course_id` = v_c3 AND `chapter_no` = '2');
    INSERT INTO `knowledge_point` (`course_id`, `chapter_id`, `kp_code`, `kp_name`, `is_key`, `is_hard`, `mastery_threshold`) VALUES
      (v_c3, v_ch, 'MA301-05', '定积分应用', 1, 1, 65),
      (v_c3, v_ch, 'MA301-06', '级数收敛判定', 1, 1, 70),
      (v_c3, v_ch, 'MA301-07', '泰勒展开', 1, 1, 70),
      (v_c3, v_ch, 'MA301-08', '微分方程初步', 0, 0, 60);

    -- ---------- 题库 ----------
    INSERT INTO `question` (`course_id`, `kp_id`, `question_type`, `difficulty`, `content`, `answer`, `source_type`, `estimated_minutes`)
    SELECT kp.`course_id`, kp.`id`, 1, 3 - (kp.`is_hard`),
           CONCAT(kp.`kp_name`, ' 基础练习第 1 题'), 'A', 2, 5
    FROM `knowledge_point` kp;
    INSERT INTO `question` (`course_id`, `kp_id`, `question_type`, `difficulty`, `content`, `answer`, `source_type`, `estimated_minutes`)
    SELECT kp.`course_id`, kp.`id`, 1, 3 + (kp.`is_hard`),
           CONCAT(kp.`kp_name`, ' 进阶练习第 2 题'), 'B', 2, 8
    FROM `knowledge_point` kp;

    -- ---------- 作业 ----------
    INSERT INTO `homework` (`course_id`, `class_id`, `teacher_id`, `title`, `homework_type`, `publish_at`, `deadline`, `status`)
    VALUES (v_c1, v_cls1, v_t1, '数据结构 第 1 章作业', 1, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), 1),
           (v_c1, v_cls1, v_t1, '数据结构 第 2 章作业', 1, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 2),
           (v_c2, v_cls2, v_t2, '大学英语 阅读理解训练', 1, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
           (v_c2, v_cls2, v_t2, '大学英语 写作练习', 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 2),
           (v_c3, v_cls3, v_t3, '高等数学 极限专项', 1, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY), 1),
           (v_c3, v_cls3, v_t3, '高等数学 积分专项', 1, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 2);

    INSERT INTO `homework_question` (`homework_id`, `question_id`, `sort_order`, `score`)
    SELECT h.`id`, q.`id`, 1, 10
    FROM `homework` h
    JOIN `question` q ON q.`course_id` = h.`course_id`
    WHERE q.`content` LIKE '%基础练习第 1 题';

    -- ---------- 近 30 天明细数据 ----------
    SET v_d = 0;
    WHILE v_d < 30 DO
      SET v_date = DATE_SUB(CURDATE(), INTERVAL v_d DAY);

      -- 每日学习统计：每名学生 × 每门课程
      INSERT IGNORE INTO `study_stat_daily`
        (`user_id`, `course_id`, `stat_date`, `effective_minutes`, `qa_count`, `homework_count`,
         `exercise_count`, `correct_count`, `focus_score`, `review_finished`, `avg_answer_seconds`)
      SELECT u.`id`, c.`id`, v_date,
             30 + ((u.`id` * 13 + c.`id` * 7 + DAY(v_date) * 11) % 95),
             (u.`id` + c.`id` + DAY(v_date)) % 5,
             (u.`id` + DAY(v_date)) % 2,
             15,
             8 + ((u.`id` * 5 + c.`id` * 3 + DAY(v_date)) % 7),
             58 + ((u.`id` + DAY(v_date) * 3) % 38),
             (u.`id` + DAY(v_date)) % 3,
             40 + ((u.`id` + DAY(v_date) * 2) % 70)
      FROM `sys_user` u
      CROSS JOIN `course` c
      WHERE u.`user_no` LIKE 'S2024%' AND c.`course_code` IN ('CS101', 'EN201', 'MA301');

      -- 作答明细：每名学生每天约 6 条
      INSERT INTO `answer_record`
        (`user_id`, `question_id`, `kp_id`, `source_type`, `is_correct`, `score`, `used_seconds`, `answer_time`, `create_time`)
      SELECT u.`id`, q.`id`, q.`kp_id`, 1,
             IF((u.`id` + q.`id` + v_d) % 3 = 0, 0, 1),
             IF((u.`id` + q.`id` + v_d) % 3 = 0, 0, 10),
             30 + ((u.`id` * 7 + q.`id` * 3 + v_d * 5) % 150),
             TIMESTAMP(v_date, SEC_TO_TIME(8 * 3600 + ((u.`id` * 7 + q.`id` * 3 + v_d * 5) % 720) * 60)),
             TIMESTAMP(v_date, SEC_TO_TIME(8 * 3600 + ((u.`id` * 7 + q.`id` * 3 + v_d * 5) % 720) * 60))
      FROM `sys_user` u
      JOIN (SELECT `id`, `kp_id` FROM `question` WHERE `id` % 8 = 0) q
      WHERE u.`user_no` LIKE 'S2024%';

      -- AI 调用日志：每名学生每天 2 次
      INSERT INTO `ai_usage_log`
        (`user_id`, `model_name`, `scene_type`, `prompt_tokens`, `completion_tokens`, `total_tokens`,
         `duration_ms`, `status`, `create_time`)
      SELECT u.`id`, 'deepseek-v4-flash',
             1 + ((u.`id` + v_d * 2) % 5),
             280 + ((u.`id` * 7 + v_d * 13) % 520),
             140 + ((u.`id` * 11 + v_d * 17) % 420),
             420 + ((u.`id` * 7 + v_d * 13) % 520) + ((u.`id` * 11 + v_d * 17) % 420),
             700 + ((u.`id` * 13 + v_d * 19) % 2300),
             IF((u.`id` + v_d) % 23 = 0, 0, 1),
             TIMESTAMP(v_date, SEC_TO_TIME(9 * 3600 + ((u.`id` * 5 + v_d * 7) % 600) * 60))
      FROM `sys_user` u WHERE u.`user_no` LIKE 'S2024%';

      INSERT INTO `ai_usage_log`
        (`user_id`, `model_name`, `scene_type`, `prompt_tokens`, `completion_tokens`, `total_tokens`,
         `duration_ms`, `status`, `create_time`)
      SELECT u.`id`, 'deepseek-v4-flash',
             1 + ((u.`id` + v_d * 3) % 5),
             320 + ((u.`id` * 9 + v_d * 11) % 480),
             160 + ((u.`id` * 13 + v_d * 7) % 380),
             480 + ((u.`id` * 9 + v_d * 11) % 480) + ((u.`id` * 13 + v_d * 7) % 380),
             650 + ((u.`id` * 17 + v_d * 11) % 2500),
             1,
             TIMESTAMP(v_date, SEC_TO_TIME(14 * 3600 + ((u.`id` * 11 + v_d * 3) % 540) * 60))
      FROM `sys_user` u WHERE u.`user_no` LIKE 'S2024%';

      -- 答疑会话
      INSERT INTO `chat_session`
        (`session_no`, `user_id`, `course_id`, `scene_type`, `title`, `status`, `message_count`, `last_message_at`, `create_time`)
      SELECT CONCAT('DEMO-', u.`id`, '-', v_d), u.`id`, v_c1,
             1 + ((u.`id` + v_d) % 5),
             '关于知识点的答疑', 0, 3,
             TIMESTAMP(v_date, SEC_TO_TIME(10 * 3600 + ((u.`id` * 3 + v_d * 5) % 600) * 60)),
             TIMESTAMP(v_date, SEC_TO_TIME(10 * 3600 + ((u.`id` * 3 + v_d * 5) % 600) * 60))
      FROM `sys_user` u WHERE u.`user_no` LIKE 'S2024%';

      INSERT INTO `chat_message`
        (`session_id`, `sender_role`, `msg_type`, `content`, `reply_duration_ms`, `create_time`)
      SELECT s.`id`, r.`sender_role`, 1,
             CASE r.`sender_role` WHEN 1 THEN '这个概念我不太理解，能再讲讲吗？' ELSE '我们从定义入手，先看一个具体例子。' END,
             IF(r.`sender_role` = 2, 800 + (s.`id` % 1500), NULL),
             DATE_ADD(s.`create_time`, INTERVAL r.`seq` MINUTE)
      FROM `chat_session` s
      JOIN (SELECT 1 AS `seq`, 1 AS `sender_role`
            UNION ALL SELECT 2, 2
            UNION ALL SELECT 3, 1) r
      WHERE s.`session_no` LIKE 'DEMO-%';

      SET v_d = v_d + 1;
    END WHILE;

    -- ---------- 作业提交 ----------
    INSERT INTO `homework_submission`
      (`homework_id`, `user_id`, `submit_status`, `submit_at`, `used_seconds`, `objective_score`, `total_score`, `grade_status`)
    SELECT h.`id`, cm.`user_id`,
           IF((h.`id` + cm.`user_id`) % 9 = 0, 0, 2),
           IF((h.`id` + cm.`user_id`) % 9 = 0, NULL, DATE_SUB(NOW(), INTERVAL ((h.`id` + cm.`user_id`) % 7) DAY)),
           600 + ((h.`id` * 31 + cm.`user_id` * 17) % 2400),
           IF((h.`id` + cm.`user_id`) % 9 = 0, NULL, 6 + ((h.`id` + cm.`user_id`) % 5)),
           IF((h.`id` + cm.`user_id`) % 9 = 0, NULL, 60 + ((h.`id` * 7 + cm.`user_id` * 3) % 40)),
           IF((h.`id` + cm.`user_id`) % 9 = 0, 0, 2)
    FROM `homework` h
    JOIN `class_member` cm ON cm.`class_id` = h.`class_id` AND cm.`member_type` = 1;

    -- ---------- 知识点掌握度 ----------
    INSERT INTO `knowledge_mastery`
      (`user_id`, `kp_id`, `course_id`, `mastery_score`, `accuracy_rate`, `avg_answer_seconds`,
       `review_completion`, `exercise_total`, `weak_flag`, `last_calc_at`)
    SELECT u.`id`, kp.`id`, kp.`course_id`,
           32 + ((u.`id` * 17 + kp.`id` * 23) % 64),
           35 + ((u.`id` * 13 + kp.`id` * 29) % 60),
           40 + ((u.`id` + kp.`id` * 7) % 90),
           40 + ((u.`id` * 3 + kp.`id` * 11) % 60),
           20 + ((u.`id` + kp.`id`) % 80),
           IF(32 + ((u.`id` * 17 + kp.`id` * 23) % 64) < kp.`mastery_threshold`, 1, 0),
           NOW()
    FROM `sys_user` u
    CROSS JOIN `knowledge_point` kp
    WHERE u.`user_no` LIKE 'S2024%';

    -- ---------- 当日实时数据（保证大屏首屏有值） ----------
    INSERT IGNORE INTO `ai_usage_log`
      (`user_id`, `model_name`, `scene_type`, `prompt_tokens`, `completion_tokens`, `total_tokens`,
       `duration_ms`, `status`, `create_time`)
    SELECT u.`id`, 'deepseek-v4-flash', 1, 420, 210, 630,
           900 + (u.`id` % 800), 1, NOW()
    FROM `sys_user` u WHERE u.`user_no` LIKE 'S2024%';

    INSERT INTO `demo_seed_log` (`seed_key`) VALUES ('dashboard_v1');
    SELECT '演示数据生成完成' AS message;
  END IF;
END$$

DELIMITER ;

CALL `seed_dashboard_demo`();
DROP PROCEDURE IF EXISTS `seed_dashboard_demo`;
