# Redis 操作分层重构方案

| 项 | 内容 |
| --- | --- |
| 文档版本 | v1.0（重构已落地） |
| 创建日期 | 2026-11-24；落地日期 2026-11-26 |
| 适用项目 | `student-digital-human/backend`（Spring Boot + Spring Data Redis） |
| 当前状态 | **分层重构已完成：9 个 RedisRepository 全部落地，业务层 Redis 底层调用已收口，编译通过、18 个测试全绿。扫码模拟模块仍搁置，未纳入本次重构。** |
| 关联代码 | `common/RedisKeys.java`、`module/auth`、`module/rbac`、`module/dashboard`、`module/privatechat`、`framework/security`；`module/qr` 暂不纳入 |
| 关联文档 | `docs/架构设计文档.md` |

> v1.0 修订说明：协作约束解除后按本方案完成落地。除原盘点模块外，v1.7 私信模块（在线状态、Pub/Sub、消息发号）一并收口；`dashboard:analytics:` 与 `ai_tutor:seq:conv:` 前缀已登记进 `RedisKeys`，实际 key 字符串保持不变。

---

## 0. 落地情况说明

### 0.1 实施前提已满足

2026-11-26 协作约束解除：本地代码已是最新版本，确认无其他成员并行修改，重构在当前工作区直接开展。扫码模块仍处搁置状态，按非目标处理，未纳入本次重构。

### 0.2 落地结果

- 新增 9 个按业务域划分的 `RedisRepository`，覆盖认证、RBAC、大屏、私信、风控全部 Redis 使用点；
- Controller、Filter、Service、MQ Consumer 中的 `StringRedisTemplate`、Lua 脚本、JSON 序列化、key 拼接全部下沉，业务层只保留领域方法调用；
- `RedisKeys` 新登记 `DASHBOARD_ANALYTICS_PREFIX`、`MESSAGE_SEQ` 两个常量，实际 key 字符串、value 格式、TTL、降级策略均未改变；
- `mvn compile` 通过；`mvn test` 18 个测试全部通过（本机若设置了 `JWT_SECRET` 用户环境变量，`JwtUtilTest` 会出现与本次重构无关的环境性失败，临时清除该进程变量后全绿）。

### 0.3 仍按非目标处理的事项

- 扫码模块维持现状，待恢复或删除决策明确后再处理；
- `KEYS` 改 `SCAN`、Set 索引治理、JWT 黑名单摘要化等行为优化仍另行排期，未混入本次结构重构。

---

## 1. 背景与目标

### 1.1 背景

项目已经在认证登录、RBAC 权限缓存、数据大屏缓存、接口限流等场景使用 Redis，并通过 `RedisKeys.java` 维护了大部分 key 模板。但 Redis 的具体读写命令、JSON 序列化、TTL、异常降级仍直接散落在 Service、Controller、Filter、MQ Consumer 和风控组件中。

随着 Redis 使用点增多，业务类中直接出现 `StringRedisTemplate`、`opsForValue()`、`opsForSet()`、`execute(LuaScript)` 会带来：

- 业务流程与 Redis 数据结构细节耦合；
- JSON 序列化、key 拼接、TTL 设置在多个类中重复；
- Controller、Filter、MQ Consumer 直接接触 Redis 细节，分层边界不清晰；
- 同一类 Redis 数据的清理、续期、异常处理策略容易不一致；
- 单元测试需要 mock 较多 RedisTemplate 底层 API；
- `dashboard:analytics:*` 等个别 key 前缀尚未收入 `RedisKeys`，key 目录不完整；
- 在版本落后、并行开发较多时，大范围结构调整会放大合并冲突。

### 1.2 重构目标

1. **外部行为零变更**：Redis key、value 格式、数据类型、TTL、HTTP 接口、数据库结构均保持不变。
2. **分层职责清晰**：Service / Guard 负责业务编排，Redis Repository 负责 key、序列化、TTL 下发和 Redis 命令。
3. **收口重复逻辑**：统一 key 拼装、JSON 读写、Set/ZSet 操作和 Redis 异常边界。
4. **提升可读性与可测试性**：业务层只出现领域方法，例如 `saveWhitelist`、`existsBlacklist`、`tryAcquireSendLock`。
5. **控制合并冲突**：按业务域拆成小 PR，避免一次性移动大量文件或长期维护重构分支。
6. **支持增量迁移与回滚**：每个小阶段都能独立编译、测试、提交和 revert。
7. **不更换技术栈**：第一阶段继续使用 `spring-boot-starter-data-redis` 和 `StringRedisTemplate`，不强制引入 Spring Cache 或 Redisson。

### 1.3 非目标

以下内容不纳入第一阶段纯分层重构：

- **当前代码未同步前不启动实际结构改造**；
- **不重构已搁置的扫码模拟模块**，除非团队决定恢复该功能；
- 不更换 Redis 客户端、部署方式或 db0 数据；
- 不做 Redis 数据迁移、不清空现有 Redis；
- 不修改 Redis key 前缀、JSON 字段名和现有 TTL 业务值；
- 不调整登录、刷新、登出、限流、缓存失效的业务规则；
- 不进行大规模包名、类名重命名或整文件格式化；
- 不把 MyBatis Mapper 改造成 Redis 操作类；
- 不强制引入 `@Cacheable`、Redisson 或新的抽象框架；
- 不把 `KEYS` 替换、Set 索引治理、JWT 摘要化等行为优化混入结构重构。

---

## 2. 现状盘点（基于 2026-11-24 本地快照）

> 注意：当前本地版本落后于团队协作状态。正式启动前必须在最新代码上重新执行 Redis 调用点扫描，下表仅作为方案设计基线。

| 当前类 | Redis 数据 | 主要操作 | 规划归属 | 首批建议 |
| --- | --- | --- | --- | --- |
| `module/auth/controller/AuthController.java` | `ai_tutor:sms:limit:{phone}`，String，60 秒 | `setIfAbsent` 短信发送频控 | `SmsLimitRedisRepository` | 候选，但需确认 `AuthController` 是否有并行修改 |
| `module/auth/service/AuthServiceImpl.java` | `ai_tutor:auth:refresh:whitelist:{jti}`，String，值为 userId，7 天 | `GET/SET/DEL` refresh 白名单 | `RefreshTokenRedisRepository` | 与认证链路一起改造，避免反复修改同一文件 |
| `module/auth/service/AuthServiceImpl.java` | `ai_tutor:auth:refresh:user:{userId}`，Set，成员为 jti | `SADD/SREM/EXPIRE` 用户登录索引 | `RefreshTokenRedisRepository` | 同上 |
| `module/auth/service/AuthServiceImpl.java` | `ai_tutor:auth:blacklist:{accessJwt}`，String，TTL 为 token 剩余有效期 | 登出时写入黑名单 | `AccessTokenBlacklistRedisRepository` | 与 JWT 过滤器、登出链路一起改造 |
| `framework/security/JwtAuthenticationFilter.java` | `ai_tutor:auth:blacklist:{accessJwt}` | 请求鉴权时 `hasKey` 判断 | `AccessTokenBlacklistRedisRepository` | 需安全模块负责人评审 |
| `module/qr/service/QrLoginServiceImpl.java` | `ai_tutor:qr:login:{qrToken}`，String JSON，120 秒 | 扫码会话 JSON 的 `GET/SET` | 未来：`QrSessionRedisRepository` | **暂缓，模块已搁置** |
| `framework/security/RbacUserDetailsService.java` | `ai_tutor:rbac:perms:{userId}`，String JSON 数组，24 小时 | 权限缓存读取、回写、删除 | `PermissionCacheRedisRepository` | 可作为低冲突候选，前提是 RBAC 近期无并行改动 |
| `module/dashboard/service/DashboardServiceImpl.java` | `dashboard:analytics:{days}:{courseId}`，String JSON，60～75 秒 | 大屏聚合快照缓存 | `DashboardCacheRedisRepository` | 可作为低冲突候选，前提是大屏模块近期无并行改动 |
| `module/dashboard/mq/DashboardEventConsumer.java` | `dashboard:analytics:*` | `KEYS + DELETE` 批量失效 | `DashboardCacheRedisRepository` | 与大屏缓存同一个 PR 完成 |
| `framework/security/AntiCrawlerGuard.java` | `anti:ban`、`anti:violation`、`anti:rate`、登录失败与锁定 key | `hasKey/getExpire/INCR/SET/DEL`、ZSet Lua | `AntiCrawlerRedisRepository` | 放在业务缓存之后，因 Lua 与降级策略较敏感 |
| `module/privatechat/service/OnlineUserService.java` | `ai_tutor:ws:online:{userId}:{sessionId}`，String，60 秒 | `SET/DEL`、`SCAN` 在线判断、`hasKey` 半开连接清理 | `OnlineStateRedisRepository` | v1.7 新增，已落地 |
| `module/privatechat/service/PrivatePushService.java` | `ai_tutor:ws:private` Pub/Sub 频道 | `convertAndSend` 跨实例广播 | `PrivateChannelRedisRepository` | v1.7 新增，已落地 |
| `module/privatechat/service/MessageSeqService.java` | `ai_tutor:seq:conv:{conversationId}`，String，7 天 | `INCR` 发号、基线 `SET`、`EXPIRE` | `MessageSeqRedisRepository` | v1.7 新增，已落地 |
| `common/RedisKeys.java` | 全部 `ai_tutor:*` 与 `dashboard:*` key 模板 | 常量定义 | 保留为全局 key 目录 | 已补登大屏前缀与发号器常量 |

### 2.1 当前已有基础

- 大部分 key 已统一以 `ai_tutor:` 开头；
- `RedisKeys.java` 对 key 用途和 TTL 有基础注释；
- 主要使用 `StringRedisTemplate`，value 可读性较好；
- 权限、大屏等对象采用 JSON 字符串，便于调试和跨语言查看；
- 限流使用 Lua 脚本保证滑动窗口操作原子性；
- 风控组件已有 Redis 异常时降级放行的意识。

### 2.2 现存技术问题

1. Controller、Filter、MQ Consumer 直接接触 Redis 命令，分层边界不统一。
2. JSON 序列化、key 拼接、TTL 设置在多个类中重复。
3. `AntiCrawlerGuard` 同时维护 Lua 脚本、Redis 命令、阈值判断、结果转换和降级日志。
4. `dashboard:analytics:*` 尚未收入 `RedisKeys`。
5. Redis Set 的单个 member 不能单独设置 TTL，`refresh:user:{userId}` 可能残留过期 jti。
6. `DashboardEventConsumer` 当前使用 `KEYS`，key 数量增大后存在阻塞风险。
7. 业务类直接依赖 `StringRedisTemplate`，单元测试 mock 成本较高。
8. 当前本地版本落后且团队改动未齐，继续在旧快照上做结构调整会使上述盘点和实际代码出现偏差。

---

## 3. 目标分层结构

### 3.1 分层示意

```text
Controller / Filter / MQ Consumer
                ↓
        Service / Guard
        业务编排、状态机、阈值与降级策略
                ↓
 ┌──────────────┴──────────────┐
 MySQL Mapper              RedisRepository / RedisStore
 MyBatis + SQL             StringRedisTemplate + JSON + TTL + Set/ZSet/Lua
        ↓                         ↓
      MySQL                     Redis
```

- **Controller**：处理 HTTP 参数、响应和鉴权上下文，不直接调用 `StringRedisTemplate`。
- **Service**：负责业务流程、状态机、TTL 业务时长、异常语义和事务编排。
- **RedisRepository**：负责 key 拼接、Redis 数据结构选择、JSON 序列化和 Redis 命令执行。
- **MyBatis Mapper**：继续负责 MySQL，不混入 Redis 操作。
- **Filter / Guard**：保留安全过滤和风控策略，底层 Redis 读写调用 Repository。

### 3.2 建议包结构

第一阶段按业务域放置，不急于新建大而全的 `common/redis` 抽象层：

```text
com.edu.aitutor
├─ common
│  └─ RedisKeys.java                 # 全局 key 目录
├─ module
│  ├─ auth
│  │  └─ repository
│  │     ├─ RefreshTokenRedisRepository.java
│  │     └─ SmsLimitRedisRepository.java
│  ├─ rbac
│  │  └─ repository
│  │     └─ PermissionCacheRedisRepository.java
│  ├─ dashboard
│  │  └─ repository
│  │     └─ DashboardCacheRedisRepository.java
│  └─ privatechat
│     └─ repository
│        └─ redis
│           ├─ OnlineStateRedisRepository.java
│           ├─ PrivateChannelRedisRepository.java
│           └─ MessageSeqRedisRepository.java
└─ framework
   └─ security
      └─ redis
         ├─ AccessTokenBlacklistRedisRepository.java
         └─ AntiCrawlerRedisRepository.java
```

暂不纳入首批：

```text
module/qr/repository/QrSessionRedisRepository.java
```

`module/qr` 为已搁置的模拟扫码模块。若后续恢复开发，再按同一规范新增 Repository；若决定删除该模块，则随模块清理一起处理，不提前投入重构。

### 3.3 命名与方法边界

类名建议统一使用 `XxxRedisRepository`。如果团队更偏好缓存语义，也可统一为 `XxxRedisStore` 或 `XxxRedisCache`，但不要多种后缀混用。

Repository 方法应使用业务语言：

| 不推荐在 Service 中出现 | 推荐 Repository 方法 |
| --- | --- |
| `opsForValue().set(...)` | `saveWhitelist(userId, jti, ttl)` |
| `opsForValue().get(...)` | `findWhitelistUserId(jti)` |
| `hasKey(blacklistKey)` | `existsBlacklist(accessToken)` |
| `opsForSet().add(...)` | `addUserJti(userId, jti, ttl)` |
| `opsForSet().remove(...)` | `removeUserJti(userId, jti)` |
| `setIfAbsent(smsKey, "1", ttl)` | `tryAcquireSendLock(phone, ttl)` |
| `keys("dashboard:analytics:*")` | `evictAllAnalyticsCache()` |

不建议创建包含所有业务 Redis 操作的万能 `RedisMapper` 或 `RedisDao`。

---

## 4. Redis 编码规范

### 4.1 Key 规范

1. 统一使用小写英文、数字和冒号分层：

   ```text
   项目名:模块名:业务名:唯一标识
   ```

2. 正式 key 模板统一在 `RedisKeys.java` 登记，注释说明类型、value、TTL、写入时机和删除/过期时机。
3. key 中避免放置大段 JSON。
4. key 片段包含冒号时需谨慎，例如 IPv6 地址 `::1` 会让 GUI 分层混乱；后续可单独评估编码方式，但这属于 key 变更，不能混入本次分层重构。
5. 生产环境避免使用 `KEYS *`，应使用 `SCAN` 或维护业务索引集合。

### 4.2 Value 与数据结构

- 简单值、JSON 对象：String；
- 唯一成员集合、索引：Set；
- 时间窗口、排行榜、带分数排序：ZSet；
- 对象字段需要单独读写：再考虑 Hash；
- 图片、文件、音视频等大对象放 MinIO/OSS，Redis 只存 ID、URL 或短期状态；
- Redis 不作为复杂关系查询业务数据的唯一数据源，MySQL 仍是正式数据源。

### 4.3 序列化规范

第一阶段继续使用：

```text
StringRedisTemplate + 现有 Jackson ObjectMapper
```

- 保持现有 JSON 字段名和时间格式；
- 不切换为 JDK 二进制序列化；
- 第一阶段不更换为 `GenericJackson2JsonRedisSerializer`，避免新旧缓存格式不一致；
- Repository 内部统一处理对象与 JSON 字符串转换，Service 不直接调用 `writeValueAsString`。

### 4.4 TTL 规范

- 除明确的永久基础数据外，Redis key 必须设置 TTL；
- TTL 与业务生命周期对齐：短信锁 60 秒、refresh 白名单 7 天、JWT 黑名单等于 token 剩余有效期、权限缓存 24 小时、大屏缓存 60～75 秒、限流窗口等于统计窗口；
- 缓存类 key 可保留随机抖动，避免大量 key 同时失效；
- Set/Hash 的 TTL 作用于整个 key，不能误认为内部单个 member 有独立 TTL。

### 4.5 异常与降级

重构时必须保持现有失败策略：

- 大屏缓存：Redis 读取失败可回源 MySQL，写入失败记录日志且不影响主查询；
- 权限缓存：反序列化失败后回源 MySQL，缓存写入失败只记录日志；
- 限流风控：Redis 不可用时当前策略为告警并降级放行，策略仍由 `AntiCrawlerGuard` 决定；
- 认证白名单/黑名单：属于安全链路，异常处理策略第一阶段原样平移，不擅自改成“全部放行”或“全部拒绝”；
- Repository 默认执行 Redis 操作并抛出底层异常；是否捕获、降级或转换为业务异常，由 Service / Guard 按语义决定。纯 JSON 反序列化兜底若原本属于存储细节，也可移入 Repository，但必须保持行为一致。

---

## 5. 目标类职责与示例

### 5.1 短信频控 Repository（小型候选）

```java
@Repository
public class SmsLimitRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public SmsLimitRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquireSendLock(String phone, Duration ttl) {
        Boolean allowed = redisTemplate.opsForValue().setIfAbsent(
                String.format(RedisKeys.SMS_LIMIT, phone), "1", ttl);
        return Boolean.TRUE.equals(allowed);
    }
}
```

`AuthController` 中只替换 Redis 细节，仍保留原有“频繁发送”异常和短信发送流程：

```java
if (!smsLimitRedisRepository.tryAcquireSendLock(dto.getPhone(), Duration.ofSeconds(60))) {
    throw new BusinessException(ErrorCode.TOO_FREQUENT, "验证码发送过于频繁，请60秒后再试");
}
smsService.sendVerifyCode(dto.getPhone());
```

是否将该逻辑从 Controller 下沉到 SmsService，应作为单独的分层整理，不与 Redis Repository 抽取强行绑定。

### 5.2 Refresh Token Repository

建议方法：

```text
saveWhitelist(userId, jti, ttl)
findWhitelistUserId(jti)
deleteWhitelist(jti)
addUserJti(userId, jti, ttl)
removeUserJti(userId, jti)
findUserJtis(userId)
```

重构后 `AuthServiceImpl` 中表达业务动作：

```java
refreshTokenRedisRepository.saveWhitelist(
        user.getId(), jti, Duration.ofDays(7));
refreshTokenRedisRepository.addUserJti(
        user.getId(), jti, Duration.ofDays(7));
```

登出时：

```java
refreshTokenRedisRepository.deleteWhitelist(jti);
refreshTokenRedisRepository.removeUserJti(userId, jti);
```

### 5.3 Access Token 黑名单 Repository

建议方法：

```text
blacklist(accessToken, ttl)
existsBlacklist(accessToken)
```

- 写入方：`AuthServiceImpl.logout`；
- 判断方：`JwtAuthenticationFilter`；
- Repository 只负责 key 拼接、TTL 和 `SET/EXISTS`，不改变过滤器异常处理。

### 5.4 权限缓存 Repository

建议方法：

```text
find(userId) -> Optional<List<String>> 或 nullable List<String>
save(userId, permCodes, ttl)
evict(userId)
```

内部统一使用现有 `TypeReference<List<String>>` 和 Jackson 反序列化逻辑。`RbacUserDetailsService` 仍负责查 MySQL、组装 `GrantedAuthority` 和决定缓存 TTL。

### 5.5 大屏缓存 Repository

建议方法：

```text
findAnalytics(cacheKey)
saveAnalytics(cacheKey, snapshot, ttl)
evictAllAnalyticsCache()
```

- `DashboardServiceImpl` 保留缓存 key 中的天数、课程 ID 组装以及 TTL 随机抖动策略；
- `DashboardEventConsumer` 只调用 `evictAllAnalyticsCache()` 和 WebSocket 推送；
- 第一阶段可平移现有 `KEYS + DELETE` 命令，第二阶段再单独优化为 `SCAN + DELETE`。

### 5.6 风控 Repository

`AntiCrawlerRedisRepository` 负责下沉：

- 滑动窗口 Lua 脚本对象与 `execute` 调用；
- 封禁 key 的写入、存在判断、TTL 查询；
- 违规计数 `INCR + EXPIRE`；
- 登录失败计数和锁定 key 操作；
- 底层 key 删除。

`AntiCrawlerGuard` 保留：

- 规则匹配和阈值计算；
- `AntiCrawlerProperties` 配置读取；
- 风控开关；
- Redis 异常时的降级策略；
- `RateLimitResult` 结果转换；
- 日志脱敏和提示文案。

### 5.7 扫码模块处理原则

`module/qr` 当前不进入首批改造：

- 如果模块继续搁置：保持现状，避免无效重构；
- 如果未来恢复开发：再新增 `QrSessionRedisRepository`，按本文档规范迁移 JSON 会话读写；
- 如果确认删除：在模块删除变更中一并清理 `ai_tutor:qr:login:*` 相关代码和 `RedisKeys.QR_LOGIN` 常量。

---

## 6. 分阶段实施计划

> 本章为落地前的原计划，保留作过程参考；实际落地结果见第 0 章，各业务域已一次性完成迁移，扫码模块仍按阶段约定暂缓。

### 阶段 0A：当前等待期（现在即可执行）

1. 不在落后工作区开展多文件结构重构。
2. 由团队成员将已有改动提交到各自分支，或导出补丁、说明未提交文件范围。
3. 明确团队当前集成分支（通常为 `dev`）和后续协作窗口。
4. 继续评审本文档，确认包名、类名、首批模块和分支策略。
5. 如确需提前试做，只允许在独立草稿分支或项目副本中进行，不直接污染当前工作区。

### 阶段 0B：代码同步与启动检查

等团队代码可获取后，先整理本地已有改动，再按团队 Git 规范同步。常用检查命令：

```powershell
git status
git fetch --all --prune
git log --oneline --decorate --graph --all -n 20
```

若当前分支已配置上游，可查看落后提交和变更文件：

```powershell
git log --oneline HEAD..@{u}
git diff --name-only HEAD...@{u}
```

若未配置上游，将 `@{u}` 替换为实际集成分支，例如 `origin/dev`。同步方式使用 `pull --rebase` 或 merge，应遵循团队约定；存在未提交改动时先提交或暂存，避免拉取冲突与本地改动混杂。

同步后必须重新扫描 Redis 使用点：

```text
StringRedisTemplate
RedisTemplate
redisTemplate.
RedisKeys.
@Cacheable
DefaultRedisScript
```

并重新执行后端基线测试：

```powershell
cd backend
.\mvnw.cmd test
```

### 阶段 1：按低冲突、小 PR 迁移

正式实施时按业务域拆 PR，避免一次性大改。建议顺序不是强制顺序，最终以“目标文件近期是否有并行修改”为准：

1. **RBAC 权限缓存**：若 `RbacUserDetailsService` 近期无成员修改，可作为缓存类示范。
2. **大屏缓存**：若大屏模块由当前负责人维护，可将 Service 与 MQ Consumer 一起迁移。
3. **认证与安全切片**：`AuthController`、`AuthServiceImpl`、`JwtAuthenticationFilter` 关联较强，建议在同一个短周期 PR 中完成短信锁、refresh 白名单、用户 Set 和黑名单迁移，避免短期内反复修改 `AuthServiceImpl`。
4. **风控组件**：在业务缓存和认证链路稳定后，再迁移 `AntiCrawlerGuard`。
5. **扫码模块**：暂缓，待恢复或删除决策明确后处理。

每个 PR 都应满足：

- 只处理一个业务域或一组强相关 Redis key；
- 不做无关格式化、包重命名或业务逻辑调整；
- 每迁移一个模块立即编译；
- PR 描述中列出迁移的 key、Redis 类型、TTL 和行为不变说明；
- 合并后尽快进入下一阶段，避免长期存在重构分支。

### 阶段 2：风控组件分层

1. 新增 `AntiCrawlerRedisRepository`；
2. 下沉 Lua 脚本和底层 Redis 命令；
3. `AntiCrawlerGuard` 保留策略、结果对象和降级逻辑；
4. 重点验证限流、封禁、登录失败锁定以及 Redis 异常时的行为与重构前一致；
5. 确保现有 `AntiCrawlerFilterTest` 通过。

### 阶段 3：独立行为优化

以下事项在分层重构合并后单独评估、单独提交：

1. `DashboardEventConsumer` 的 `KEYS` 改为 `SCAN` 分批删除；
2. 将 `dashboard:analytics:*` 收拢到 `RedisKeys`；
3. 治理 `refresh:user:{userId}` 中的过期 jti 残留，例如增加清理方法，或改用 ZSet 记录过期时间戳；若改变数据结构，需要兼容旧 Set 或短期迁移方案；
4. JWT 黑名单 key 从完整 token 改为 SHA-256 摘要；
5. 为 Repository 补充 Mockito 单测或 Testcontainers Redis 集成测试；
6. 仅在出现简单方法级缓存需求时评估 Spring Cache；仅在出现分布式锁、延迟队列等真实需求时评估 Redisson。

---

## 7. 冲突控制策略

1. **先同步再开分支**：不要基于已知落后一个提交的代码创建长期重构分支。
2. **开工前检查重叠文件**：确认 incoming commit 和成员 WIP 是否修改目标类，例如 `AuthServiceImpl`、`JwtAuthenticationFilter`、`DashboardServiceImpl`。
3. **按模块拆 PR**：优先选择当前无人并行修改的模块；高冲突模块等协作窗口。
4. **强相关改动合并在同一 PR**：例如认证白名单、黑名单和过滤器都涉及登录链路，拆得过细会反复修改同一批文件。
5. **新增 Repository 文件冲突较低，调用点替换冲突较高**：可以先评审类设计，但不建议在旧分支中长期堆积未合并的调用点修改。
6. **重构分支保持短期**：频繁同步集成分支，避免最后一次性解决大量冲突。
7. **不要混合无关改动**：结构重构、功能开发、格式化、依赖升级和 Redis 行为优化分开提交。
8. **提前沟通文件占用**：至少确认认证、安全、大屏、RBAC、风控目录的近期负责人和未提交改动。

---

## 8. 验证方案

### 8.1 自动化验证

在 `backend` 目录执行：

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test
```

现有测试至少包括：

- `AntiCrawlerFilterTest`
- `ClientIpUtilTest`
- `JwtUtilTest`

若测试因本地中间件、环境变量或落后代码失败，应先区分是基线失败还是重构引入失败。

### 8.2 人工冒烟清单

| 模块 | 操作 | Redis 预期 |
| --- | --- | --- |
| 短信频控 | 60 秒内重复请求验证码 | 第一次写入 String key，第二次返回频繁提示，key TTL 约 60 秒 |
| 密码/短信登录 | 登录成功 | 写入 refresh 白名单、用户 jti Set，并按需生成权限缓存 |
| Token 刷新 | 调用 `/api/v1/auth/refresh` | 白名单存在且 userId 匹配时签发新 access token |
| 退出登录 | 调用登出接口 | access token 进入黑名单，refresh 白名单删除，Set 中对应 jti 被移除 |
| JWT 过滤器 | 登出后携带原 access token 请求 | 黑名单命中，请求按现有安全逻辑处理 |
| RBAC 缓存 | 首次加载用户权限，再次请求 | 首次回源 MySQL 并写 Redis，后续读取 JSON 缓存；权限变更后缓存失效 |
| 大屏缓存 | 打开大屏概览 | 生成 `dashboard:analytics:*`，TTL 60～75 秒，再次查询命中缓存 |
| 大屏事件 | 收到 RabbitMQ 刷新事件 | `dashboard:analytics:*` 被删除，同时 WebSocket 推送 |
| 接口限流 | 短时间连续请求认证/通用接口 | 生成 `ai_tutor:anti:rate:*` ZSet，窗口结束后自动过期 |
| 登录保护 | 连续登录失败 | 生成失败计数和锁定 key，达到阈值后返回频繁尝试提示 |
| 扫码模块 | 本期暂缓 | 除非模块恢复，否则不作为重构验收项 |

### 8.3 Redis 只读排查命令

```powershell
docker exec -it aitutor-redis redis-cli -n 0
```

```redis
DBSIZE
SCAN 0 MATCH ai_tutor:* COUNT 100
TYPE <key>
TTL <key>
GET <key>
SMEMBERS <key>
ZRANGE <key> 0 -1 WITHSCORES
```

重构验证阶段不建议执行 `FLUSHDB`，以免清掉本地仍在使用的登录态。阶段 1 不改变 key 和 value 格式，因此也不需要清空 Redis。

### 8.4 代码评审检查项

- 是否基于最新团队代码启动；
- 是否误改 Redis key 字符串；
- 是否误改 TTL 单位或续期时机；
- 是否改变 JSON 字段名、序列化器或 null 处理；
- 是否改变异常捕获、日志级别和降级策略；
- 是否把业务逻辑错误地下沉到 Repository；
- 是否在 Controller/Filter 中残留 `StringRedisTemplate`（该 PR 范围内）；
- 是否新增不必要依赖或过度抽象；
- 是否把行为优化、格式化和结构重构混入同一提交；
- 是否避开已搁置的扫码模块，除非有明确恢复决策。

---

## 9. 风险与回滚

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 本地版本落后、成员改动未齐 | 合并冲突大、盘点过期 | 当前只维护方案；同步后重新扫描并选择低冲突窗口 |
| 长期重构分支 | 与集成分支偏离越来越远 | 按业务域拆短期 PR，合并后再做下一模块 |
| JSON 序列化格式变化 | 旧缓存无法读取 | 第一阶段保持 `StringRedisTemplate + ObjectMapper` |
| TTL 被意外修改 | 登录态或缓存生命周期变化 | Repository 方法显式接收 `Duration`，评审逐项对照原代码 |
| 异常边界变化 | 缓存故障影响主流程或安全策略改变 | 原样平移 try/catch 与降级策略，必要时本地停止 Redis 验证 |
| 扫码模块重构后被弃用 | 产生无效工作量 | 暂缓 `module/qr`，恢复或删除决策明确后再处理 |
| 现有工作区有无关文档改动 | 重构提交混入其他变更 | 只提交目标 Java 文件，不执行 `git add .` |
| 后端正在 IDEA 中运行 | 重新编译后需重启加载新类 | 编译测试通过后由开发者重启后端，不强制结束用户进程 |

回滚策略：

- 阶段 1 不改变 Redis key 和 value 格式，新旧代码可读取同一批 Redis 数据；
- 若某一模块重构后出现问题，可单独 revert 对应 PR 或提交；
- 不需要清空 Redis，也不需要数据库回滚；
- 若阶段 3 修改 Set 数据结构或黑名单 key 结构，必须单独设计兼容、迁移和回滚方案。

---

## 10. 待确认决策

实施前建议团队确认：

1. 何时能同步最新提交并收齐其他成员未提交改动；
2. 首批选择哪个“近期无并行修改”的模块作为低风险示范；
3. 类后缀统一使用 `RedisRepository`、`RedisStore` 还是 `RedisCache`；
4. 是否从最新集成分支建立 `refactor/redis-repository` 短期分支；
5. `RedisKeys.java` 第一阶段保留为全局 key 目录，后续再评估是否下沉；
6. 扫码模拟模块最终是恢复、保留搁置还是删除；
7. 阶段 3 的 `SCAN` 优化、Set 索引治理、黑名单摘要化是否另行排期；
8. 是否需要为 Redis Repository 补充 Mockito 单测或 Testcontainers 集成测试。

## 11. 最终结论

本次 Redis 分层重构已于 2026-11-26 落地完成：9 个按业务域划分的 RedisRepository 收口了全部 Redis 命令、key 拼接、序列化与 Lua 脚本，业务层只保留领域语义方法，实际 key、value、TTL 与降级策略零变更，编译通过、18 个测试全绿。已搁置的扫码模拟模块按计划跳过；`KEYS` 改 `SCAN`、Set 索引治理、黑名单摘要化等行为优化仍作为后续独立事项，不在本次结构重构范围内。
