-- 全局 RBAC 骨架建表与初始化脚本（Spring Security 方法级鉴权）。
-- 依赖：须先执行 ai_tutor.sql 创建 ai_tutor 库与 sys_user 表，本脚本不重建数据库。
-- 内容：sys_role / sys_permission / sys_user_role / sys_role_permission 四张标准 RBAC 表，
--   初始化 3 个角色、通配权限码 *:*、首批权限码，并按 sys_user.role 回填现有用户角色。
-- 约定：SQL 关键字小写；utf8mb4 / utf8mb4_general_ci；InnoDB；逻辑删除 is_deleted。
-- 幂等：建表使用 if not exists，初始化使用 insert ignore，可重复执行。
-- 边界：本脚本只做接口级 / 按钮级权限骨架，不含数据级权限与权限管理后台。
set names utf8mb4;
use `ai_tutor`;

-- ============================================================
-- 一、角色表
-- ============================================================
create table if not exists `sys_role` (
  `id` bigint unsigned not null auto_increment comment '角色id，主键',
  `role_code` varchar(64) not null comment '角色编码，唯一；加载时加 ROLE_ 前缀，如 ROLE_STUDENT',
  `role_name` varchar(64) not null comment '角色名称',
  `data_scope` tinyint unsigned not null default 1 comment '数据范围（预留）：1 全部数据，2 本班/本部门，3 本人；本次不启用',
  `status` tinyint unsigned not null default 1 comment '状态：0 停用，1 启用',
  `remark` varchar(255) null default null comment '备注',
  `create_time` datetime not null default current_timestamp comment '记录创建时间',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment '记录最近更新时间',
  `is_deleted` tinyint(1) not null default 0 comment '逻辑删除标记：0 未删除，1 已删除',
  primary key (`id`),
  unique key `uk_role_code` (`role_code`)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='角色表';

-- ============================================================
-- 二、权限表
-- ============================================================
create table if not exists `sys_permission` (
  `id` bigint unsigned not null auto_increment comment '权限id，主键',
  `parent_id` bigint unsigned not null default 0 comment '父权限id，0 为顶级；用于菜单树（预留）',
  `perm_code` varchar(128) not null comment '权限码，唯一；格式 资源:动作，如 homework:create；*:* 为通配',
  `perm_name` varchar(128) not null comment '权限名称',
  `perm_type` tinyint unsigned not null default 2 comment '权限类型：1 菜单，2 操作',
  `path` varchar(255) null default null comment '前端路由路径（菜单预留，本次不做动态菜单）',
  `component` varchar(255) null default null comment '前端组件路径（菜单预留）',
  `icon` varchar(64) null default null comment '菜单图标（预留）',
  `status` tinyint unsigned not null default 1 comment '状态：0 停用，1 启用',
  `create_time` datetime not null default current_timestamp comment '记录创建时间',
  `update_time` datetime not null default current_timestamp on update current_timestamp comment '记录最近更新时间',
  `is_deleted` tinyint(1) not null default 0 comment '逻辑删除标记：0 未删除，1 已删除',
  primary key (`id`),
  unique key `uk_perm_code` (`perm_code`),
  key `idx_parent_id` (`parent_id`),
  key `idx_perm_type` (`perm_type`)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='权限表';

-- ============================================================
-- 三、用户-角色关联表（当前一人一角色）
-- ============================================================
create table if not exists `sys_user_role` (
  `id` bigint unsigned not null auto_increment comment '主键id',
  `user_id` bigint unsigned not null comment '用户id，关联 sys_user.id',
  `role_id` bigint unsigned not null comment '角色id，关联 sys_role.id',
  `create_time` datetime not null default current_timestamp comment '记录创建时间',
  primary key (`id`),
  unique key `uk_user_role` (`user_id`, `role_id`) comment '联合唯一：同一用户不重复绑定同一角色',
  unique key `uk_user_id` (`user_id`) comment 'user_id 唯一：强制一人一角色；后续放开多角色时删除本索引',
  constraint `fk_user_role_user_id` foreign key (`user_id`) references `sys_user` (`id`) on delete restrict on update restrict,
  constraint `fk_user_role_role_id` foreign key (`role_id`) references `sys_role` (`id`) on delete restrict on update restrict
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='用户角色关联表';

-- ============================================================
-- 四、角色-权限关联表
-- ============================================================
create table if not exists `sys_role_permission` (
  `id` bigint unsigned not null auto_increment comment '主键id',
  `role_id` bigint unsigned not null comment '角色id，关联 sys_role.id',
  `permission_id` bigint unsigned not null comment '权限id，关联 sys_permission.id',
  primary key (`id`),
  unique key `uk_role_permission` (`role_id`, `permission_id`) comment '同一角色不重复绑定同一权限',
  constraint `fk_role_permission_role_id` foreign key (`role_id`) references `sys_role` (`id`) on delete restrict on update restrict,
  constraint `fk_role_permission_permission_id` foreign key (`permission_id`) references `sys_permission` (`id`) on delete restrict on update restrict
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='角色权限关联表';

-- ============================================================
-- 五、初始化三个角色（id 与 sys_user.role 数字对齐：1 学生 / 2 教师 / 3 教务）
-- ============================================================
insert ignore into `sys_role` (`id`, `role_code`, `role_name`, `data_scope`, `remark`) values
  (1, 'student', '学生', 3, '学生角色，默认仅本人相关数据'),
  (2, 'teacher', '教师', 2, '教师角色，数据范围以本班/任课为主（数据级过滤本次不启用）'),
  (3, 'admin',   '教务', 1, '教务角色，持有通配权限码 *:*，默认拥有全部权限');

-- ============================================================
-- 六、初始化权限：id=1 通配码；2~6 菜单（预留）；7 起为操作权限
-- ============================================================
insert ignore into `sys_permission`
  (`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `component`, `icon`)
values
  -- 通配权限：教务持有，命中即放行，新增接口默认无需逐个授权
  (1,  0, '*:*',             '全部权限通配', 2, null, null, null),
  -- 菜单型权限（动态菜单本次不做，仅预留字段与类型）
  (2,  0, 'menu:homework',   '作业管理菜单', 1, '/homework', 'homework/index', 'document'),
  (3,  0, 'menu:course',     '课程中心菜单', 1, '/course',   'course/index',   'reading'),
  (4,  0, 'menu:question',   '题库菜单',     1, '/question', 'question/index', 'collection'),
  (5,  0, 'menu:chat',       '智能答疑菜单', 1, '/chat',     'chat/index',     'chat-dot-round'),
  (6,  0, 'menu:exam',       '考试试卷菜单', 1, '/exam',     'exam/index',     'tickets'),
  -- 作业模块操作
  (7,  2, 'homework:list',   '作业列表',   2, null, null, null),
  (8,  2, 'homework:view',   '作业详情',   2, null, null, null),
  (9,  2, 'homework:create', '发布作业',   2, null, null, null),
  (10, 2, 'homework:update', '修改作业',   2, null, null, null),
  (11, 2, 'homework:delete', '删除作业',   2, null, null, null),
  (12, 2, 'homework:submit', '提交作业',   2, null, null, null),
  (13, 2, 'homework:grade',  '批阅作业',   2, null, null, null),
  -- 课程模块操作
  (14, 3, 'course:list',     '课程列表',   2, null, null, null),
  (15, 3, 'course:view',     '课程详情',   2, null, null, null),
  (16, 3, 'course:create',   '新建课程',   2, null, null, null),
  (17, 3, 'course:update',   '编辑课程',   2, null, null, null),
  -- 题库模块操作
  (18, 4, 'question:list',   '题目列表',   2, null, null, null),
  (19, 4, 'question:view',   '题目详情',   2, null, null, null),
  (20, 4, 'question:create', '录入题目',   2, null, null, null),
  -- 答疑 / 考试 / 个人信息操作
  (21, 5, 'chat:send',       '发送对话',   2, null, null, null),
  (22, 6, 'exam:list',       '试卷列表',   2, null, null, null),
  (23, 6, 'exam:view',       '试卷详情',   2, null, null, null),
  (24, 0, 'profile:view',    '查看个人信息', 2, null, null, null),
  (25, 0, 'profile:update',  '更新个人信息', 2, null, null, null);

-- ============================================================
-- 七、角色-权限初始化
-- ============================================================
-- 教务：仅绑定通配码，*:* 命中后全部放行，后续新增接口无需在此追加
insert ignore into `sys_role_permission` (`role_id`, `permission_id`)
select 3, `id` from `sys_permission` where `perm_code` = '*:*';

-- 教师：作业全流程（含发布/批阅，不含学生提交）、课程与题目维护、答疑、考试、个人信息
insert ignore into `sys_role_permission` (`role_id`, `permission_id`)
select 2, p.`id`
from `sys_permission` p
where p.`perm_code` in (
  'menu:homework', 'menu:course', 'menu:question', 'menu:chat', 'menu:exam',
  'homework:list', 'homework:view', 'homework:create', 'homework:update',
  'homework:delete', 'homework:grade',
  'course:list', 'course:view', 'course:create', 'course:update',
  'question:list', 'question:view', 'question:create',
  'chat:send', 'exam:list', 'exam:view', 'profile:view', 'profile:update'
);

-- 学生：作业查看与提交、课程/题目只读、答疑、考试只读、个人信息
insert ignore into `sys_role_permission` (`role_id`, `permission_id`)
select 1, p.`id`
from `sys_permission` p
where p.`perm_code` in (
  'menu:homework', 'menu:course', 'menu:question', 'menu:chat', 'menu:exam',
  'homework:list', 'homework:view', 'homework:submit',
  'course:list', 'course:view',
  'question:list', 'question:view',
  'chat:send', 'exam:list', 'exam:view', 'profile:view', 'profile:update'
);

-- ============================================================
-- 八、按 sys_user.role 回填现有用户角色（一人一角色，user_id 唯一索引兜底）
-- ============================================================
insert ignore into `sys_user_role` (`user_id`, `role_id`)
select u.`id`,
       case u.`role` when 1 then 1 when 2 then 2 when 3 then 3 else 1 end
from `sys_user` u
where u.`is_deleted` = 0;

-- ============================================================
-- 九、权限管理类操作权限（角色/权限/用户授权；教务凭 *:* 自动拥有，无需单独授权）
-- ============================================================
insert ignore into `sys_permission`
  (`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `component`, `icon`)
values
  (26, 0, 'system:role:list',       '角色列表',     2, null, null, null),
  (27, 0, 'system:role:save',       '新增角色',     2, null, null, null),
  (28, 0, 'system:role:update',     '编辑角色',     2, null, null, null),
  (29, 0, 'system:role:delete',     '删除角色',     2, null, null, null),
  (30, 0, 'system:role:assign',     '角色分配权限', 2, null, null, null),
  (31, 0, 'system:permission:tree', '权限树查询',   2, null, null, null),
  (32, 0, 'system:user:list',       '用户列表',     2, null, null, null),
  (33, 0, 'system:user:assign',     '用户分配角色', 2, null, null, null);

-- ============================================================
-- 十、导入后核对
-- ============================================================
select 'sys_role' as `table_name`, count(*) as `rows` from `sys_role`
union all
select 'sys_permission', count(*) from `sys_permission`
union all
select 'sys_user_role', count(*) from `sys_user_role`
union all
select 'sys_role_permission', count(*) from `sys_role_permission`;
