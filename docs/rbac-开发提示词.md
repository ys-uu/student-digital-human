# 结构化提示词：为 student-digital-human 落地全局 RBAC（Spring Security）

## 一、角色与目标

你是本项目的后端+前端工程师。请在现有项目 `C:\Users\YS\Desktop\Java\student-digital-human` 中，搭建一套**可扩展的全局 RBAC 骨架**，覆盖后端接口级鉴权与前端按钮级控制，作为后续所有业务模块开发的统一权限基础。本次只做骨架与机制，**不做可视化权限管理后台**，不实现具体业务模块。

## 二、技术栈与现状（务必先读，勿臆测）

- 后端：Spring Boot **4.1.1**、Java 17、MyBatis（mybatis-spring-boot-starter 4.0.0）、jjwt 0.11.5、Redis（StringRedisTemplate）、已有 `spring-security-crypto`（仅用 BCryptPasswordEncoder）。
- 前端：Vue3 + Vite + Pinia（含 pinia-plugin-persistedstate）+ Vue Router + Element Plus。
- 现有鉴权为**手写**：`interceptor\JwtInterceptor.java` + `config\WebConfig.java` + `util\JwtUtil.java`，token 黑名单存 Redis（`common\RedisKeys.java`）。
- 现有用户表 `sys_user` 含 `role` 字段（1 学生 / 2 教师 / 3 教务）。
- 三种登录（账密 / 短信 / 扫码）已跑通，登录成功后由 `JwtUtil` 签发 JWT。

**强制前置动作**：动手前先核对 Spring Boot 4.1.1 实际解析出的 Spring Security 版本与 API（`SecurityFilterChain`、`@EnableMethodSecurity`、`authorizeHttpRequests` 等），以本地依赖和官方文档为准，不要照搬网上 5.x/旧 6.x 教程；编译期找不到类时优先怀疑版本差异。

## 三、已确定的方案决策（按此执行，不要改方向）

1. 鉴权技术：**引入 Spring Security**（`spring-boot-starter-security`），方法级用 `@PreAuthorize`。
2. 数据模型：**标准 RBAC 四张表**——`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`。
3. 交付范围：**后端骨架 + 前端按钮/菜单控制**，不含权限管理后台。
4. 角色关系：**实际一人一角色**；`sys_user_role` 对 `user_id` 建唯一索引强制，注释标明后续放开多角色时删除该索引。`sys_user.role` 保留为主角色冗余字段。
5. 教务管理员：**拥有全部权限**，通过特殊通配权限码 `*:*` 实现，新增接口默认对其放行，无需逐个授权。

## 四、后端任务

### 4.1 依赖与包结构
- pom 增加 `spring-boot-starter-security`（版本交给 parent 管理，不手写版本号）。
- 新建 `com.edu.aitutor.security` 包，内部按 `config`、`filter`、`handler`、`service` 等子包组织；实体放 `entity`、mapper 放 `mapper`，遵循现有固定包结构。

### 4.2 建表与初始化 SQL（SQL 关键字小写，utf8mb4 / utf8mb4_general_ci）
- `sys_role`：id、role_code（唯一，student/teacher/admin）、role_name、data_scope（预留，1 全部/2 本班/3 本人）、status、remark、create_time、update_time、is_deleted。
- `sys_permission`：id、parent_id（默认 0，预留菜单树）、perm_code（唯一，`资源:动作`）、perm_name、perm_type（1 菜单/2 操作）、path、component、icon（菜单预留，可空）、status、create_time、update_time、is_deleted。
- `sys_user_role`：id、user_id、role_id、create_time；联合唯一 `uk_user_role(user_id, role_id)`，并对 `user_id` 建唯一索引强制一人一角色。
- `sys_role_permission`：id、role_id、permission_id；唯一 `uk_role_permission(role_id, permission_id)`。
- 初始化数据：3 个角色；通配权限码 `*:*`；按当前已有功能补充首批权限码（如 `auth:login`、`user:update`、`qr:*` 等），并为现有 `sys_user` 回填 `sys_user_role`。后续模块权限码随开发增量补充，命名统一 `模块:动作`。
- 产出独立脚本（如 `docs/sql/rbac.sql`），不要直接覆盖 `docs\sql\ai_tutor.sql`。

### 4.3 核心组件
- `SecurityConfig`：`SecurityFilterChain` Bean——关闭 csrf；`sessionManagement` 设 STATELESS；启用 CORS；`authorizeHttpRequests` 白名单 `permitAll`、其余 `authenticated`；在 `UsernamePasswordAuthenticationFilter` 前加入自定义 JWT 过滤器；配置 `AuthenticationEntryPoint` 与 `AccessDeniedHandler`。
- 白名单需与现有 `JwtInterceptor` 的免鉴权清单一致（登录、注册、短信、扫码免鉴权接口、`/api/v1/health`、`/error`、OPTIONS 预检）。
- `JwtAuthenticationFilter`：解析 Bearer token、校验 Redis 黑名单，通过后加载权限码并构造 `UsernamePasswordAuthenticationToken` 填入 `SecurityContextHolder`。
- `UserDetailsService` 实现：按账号查用户及其角色、权限码，转换为 `GrantedAuthority`；角色加 `ROLE_` 前缀用于 `hasRole`，权限码原样用于 `hasAuthority`。
- 提供权限校验 Bean（如名为 `ss`），方法 `hasPerm(String code)`：持有 `*:*` 直接返回 true，否则精确匹配；方法上统一写 `@PreAuthorize("@ss.hasPerm('homework:create')")`。
- 启动类或配置类加 `@EnableMethodSecurity`。
- `PasswordEncoder` Bean 用 `BCryptPasswordEncoder`，与现有密码哈希保持兼容。
- 异常入口/未授权处理器统一返回 `Result` 结构（code/msg/data），未登录用 1002、权限不足用 1004，与 `ErrorCode` 对齐。

### 4.4 权限缓存
- 登录后首次鉴权时把用户权限码集合写入 Redis（key 规范放入 `RedisKeys`，值为 JSON 列表），过滤器优先读缓存；当前无管理后台，提供按 userId 清除缓存的方法备用。
- 保留现有 token 黑名单机制，迁入过滤器逻辑。

### 4.5 旧鉴权下线（避免双重鉴权）
- 用 Security 过滤器链替代 `JwtInterceptor`：移除 `WebConfig` 中的拦截器注册与 CORS 配置（CORS 迁入 SecurityConfig），删除或停用 `JwtInterceptor`；`WebConfig` 若无其他职责则精简。
- 三种登录的业务逻辑与 `JwtUtil` 签发保持不变，仅把"请求校验"职责交给 Security。

## 五、前端任务

- `UserInfoVO` 或 `/auth/me` 响应增加 `permissions` 字段（权限码数组），`stores\user.js` 增加 `permissions` 状态并持久化，登录/刷新时写入。
- 新建 `src\directives\permission.js`：`v-permission="['homework:create']"`，无权限则移除该元素；另提供 `v-role` 按角色判断；在 `main.js` 全局注册。
- 路由 `meta` 支持 `roles`/`permissions`，在 `router.beforeEach` 中增加权限校验，无权限跳转或提示；现有 token 白名单逻辑保留。
- 在现有页面用 `v-permission` 给出按钮级控制示例，验证显隐效果。

## 六、明确边界（不做）

- 数据级权限（教师只能操作本班/本人数据、学生只能看自己记录）**不在本次范围**，仍由 `teacher_id`、`class_id`、`student_id` 等归属字段在业务 SQL 中过滤，RBAC 只解决"能不能执行该类操作"。
- 不做角色/权限可视化管理后台，不做动态菜单渲染（字段预留即可）。
- 不改动与本次无关的文件，不擅自重命名、移动、删除用户文件。

## 七、编码规范

- 简体中文；固定包结构 `com.edu.aitutor`；新增类保留三行注释头风格。
- SQL 关键字一律小写；表名、字段名沿用现有下划线命名。
- 分步提交，每步可独立编译；先确认 API 与版本再写代码。

## 八、验收标准

1. `mvn compile` 通过，前端 `npm run build` 通过。
2. 三种登录均正常签发 token；白名单接口可匿名访问。
3. 无 token 访问受保护接口：返回统一 `Result`、未登录码 1002；权限不足：返回 1004。
4. 有权限的 `@PreAuthorize` 接口放行，无权限拒绝；教务凭 `*:*` 可访问全部。
5. 前端 `v-permission` 按权限码正确显隐元素，路由守卫生效。
6. 现有登录、扫码、短信、健康检查等功能不回归。
