-- 4 个教师账号 + 4 门课程分别绑定；密码统一 12345678（BCrypt 哈希）
-- 幂等：INSERT IGNORE 依赖 user_no / course_code 唯一键，可重复执行

INSERT IGNORE INTO sys_user (user_no, password_hash, real_name, role, status)
VALUES
('T2024001', '$2b$12$cek8w9y3ppCRY.c3p3gQnu26j06rD/k/fXrTeVUtqXmsdCEk5gbO2', '教师一', 2, 1),
('T2024002', '$2b$12$4VYoLvuFaDNiAPxNiKvVD.batjRG1qCSZk0VhVVdQccemoBoNpPnK', '教师二', 2, 1),
('T2024003', '$2b$12$jFj.p3R5q9FyQQEqxp5NSOjNikUepaqzQSi/ZgI9FSumjxx8Y6DSC', '教师三', 2, 1),
('T2024004', '$2b$12$3hGyBppDyWaHPUZ1LuP.9OcUMDyznD9kZlDMNitDU8VXgF2MLOZxe', '教师四', 2, 1);

INSERT IGNORE INTO course (course_code, course_name, course_category, teacher_id, status) SELECT 'CS101', '数据结构', 2, id, 1 FROM sys_user WHERE user_no='T2024001';
INSERT IGNORE INTO course (course_code, course_name, course_category, teacher_id, status) SELECT 'CS102', '操作系统', 2, id, 1 FROM sys_user WHERE user_no='T2024002';
INSERT IGNORE INTO course (course_code, course_name, course_category, teacher_id, status) SELECT 'CS103', '计算机网络', 2, id, 1 FROM sys_user WHERE user_no='T2024003';
INSERT IGNORE INTO course (course_code, course_name, course_category, teacher_id, status) SELECT 'CS104', '数据库原理', 2, id, 1 FROM sys_user WHERE user_no='T2024004';
