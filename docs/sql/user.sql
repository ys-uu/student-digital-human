/*
 Navicat Premium Data Transfer

 Source Server         : Ys
 Source Server Type    : MySQL
 Source Server Version : 80046
 Source Host           : localhost:3307
 Source Schema         : aitutor

 Target Server Type    : MySQL
 Target Server Version : 80046
 File Encoding         : 65001

 Date: 14/09/2026 16:26:15
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for user
-- ----------------------------
-- 用户表，和Java User实体对应，数据库下划线命名
CREATE TABLE `user` (
                        `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键id',
                        `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '账号',
                        `password` VARCHAR(100) NOT NULL COMMENT '密码，项目正式上线要BCrypt加密，这里测试明文',
                        `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
                        `role` VARCHAR(20) NOT NULL COMMENT '角色：student / admin',
                        `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入一条学生、一条管理员
INSERT INTO `user`(`username`, `password`, `real_name`, `role`)
VALUES
    ('student01', '123456', '张三', 'student'),
    ('admin01', '123456', '管理员', 'admin');

SET FOREIGN_KEY_CHECKS = 1;
