# 大学生辅助学习 AI 数字人系统 — API 接口文档

> **文档版本**：v1.2（精简版）  
> **编写日期**：2026-09-22（v1.2 修订：2026-11-09）  
> **适用产品**：大学生辅助学习 AI 数字人系统（MVP 及后续迭代版本）  
> **对接对象**：Web 前端（H5 / PC）、移动端 H5、第三方校园系统  
> **接口总数**：86 个（v1.1 为 77 个，v1.2 新增扫码登录接口组 6 个、注册与短信登录接口 3 个）

---

## 目  录

1. [文档说明](#1-文档说明)
2. [接口通用规范](#2-接口通用规范)
3. [认证与用户](#3-认证与用户)
4. [课程与知识库](#4-课程与知识库)
5. [数字人伴侣（FR-01）](#5-数字人伴侣fr-01)
6. [聊天与会话（FR-02 / FR-03）](#6-聊天与会话fr-02--fr-03)
7. [作业与题库（FR-04）](#7-作业与题库fr-04)
8. [错题本（FR-04）](#8-错题本fr-04)
9. [预习复习与学习计划（FR-05）](#9-预习复习与学习计划fr-05)
10. [学习统计与掌握度（FR-08）](#10-学习统计与掌握度fr-08)
11. [成长报告（FR-08）](#11-成长报告fr-08)
12. [课堂资料与备考（FR-06 / FR-07）](#12-课堂资料与备考fr-06--fr-07)
13. [通知与消息](#13-通知与消息)
14. [教师后台（FR-10）](#14-教师后台fr-10)
15. [AI 能力接口](#15-ai-能力接口)
16. [附录](#16-附录)
17. [v1.3 架构扩展接口（服务拆分与 RAG）](#17-v13-架构扩展接口服务拆分与-rag)

---

## 1. 文档说明

### 1.1 编写目的

本文档面向**前端开发、第三方对接方、测试工程师**等角色，提供"大学生辅助学习 AI 数字人系统"对外暴露的 RESTful API 接口全集。

v1.1 版本以功能需求表（FR-01~FR-10）为准绳做了**瘦身**：每个功能需求保留一组核心接口，删掉重复通道、内部调试接口以及可由其他接口推导的数据出口。

### 1.2 阅读约定

| 符号 | 含义 | 对应需求 |
| --- | --- | --- |
| ✅ | MVP 阶段必须实现（P0） | FR-01~FR-05、FR-08 |
| 🟡 | V1.1 迭代实现（P1） | FR-06、FR-07、FR-09 |
| 🟢 | V2.0 实现（P2） | FR-10 |
| 🔒 | 需要鉴权 | - |
| 📌 | 关键业务接口 | - |

### 1.3 删减说明（v1.0 → v1.1）

| 删减类别 | 被删接口示例 | 理由 |
| --- | --- | --- |
| 重复通道 | 非流式发消息、重新生成回答 | 流式消息接口已覆盖 |
| 可推导数据 | 累计统计、掌握度详情、会话总结 | 由日报列表 / 掌握度列表 / 会话详情聚合可得 |
| 管理侧冗余 CRUD | 课程 / 章节 / 知识点 / 试卷的增删改 | 收敛到教师后台「知识库维护」（FR-10） |
| 内部调试接口 | RAG 检索、引导式答疑直调、切片查询、AI 审计日志 | 属于系统内部调用，不对外开放 |
| 细分变体 | 图片上传、公式识别、单字典查询 | 并入通用上传 / OCR `mode` 参数 / 字典全量接口 |
| 边缘场景 | 分享报告、家长查看、数据导出、通知设置 | 不在 FR-01~FR-10 功能范围内 |

### 1.4 术语表

| 术语 | 解释 |
| --- | --- |
| 数字人 | 系统中的虚拟学习伴侣 Avatar，支持语音/动作/形象（FR-01） |
| 知识导学 | 按课程章节自动拆解知识点并生成学习路径（FR-02） |
| 引导式答疑 | 数字人按"思路→步骤→提示→总结"四阶段辅导（FR-03） |
| 同类题 | 与错题知识点相同、难度相近的题目（FR-04） |
| 记忆曲线 | 艾宾浩斯遗忘曲线驱动的复习任务编排（FR-05） |
| 掌握度 | 基于答题统计综合计算的知识点得分（0-100）（FR-08） |

### 1.5 v1.2 变更记录（2026-11-09）

| 变更项 | 内容 |
| --- | --- |
| ➕ 新增接口组 | 3.6~3.9 扫码登录（模拟扫码 + 白名单校验）共 6 个接口，均免鉴权 |
| ✏️ 修改 3.1 | `login_type` 枚举语义明确：4=扫码登录（演示环境为模拟扫码） |
| ✏️ 修改 2.6 | 错误码新增 `1010`（二维码已过期/不存在）；白名单拒绝复用 `1004` |
| ➖ 不涉及表结构 | 二维码会话存内存/Redis（TTL 120s），白名单走配置文件 `qr.login.whitelist`，无需新建表、无需改 `sys_user` |

---

## 2. 接口通用规范

### 2.1 基础约定

| 项 | 约定 |
| --- | --- |
| 通信协议 | HTTPS（生产）/ HTTP（开发） |
| 请求方法 | GET（查询）/ POST（创建/动作）/ PUT（全量更新）/ PATCH（部分更新）/ DELETE（删除） |
| 数据格式 | 请求与响应均为 `application/json; charset=UTF-8` |
| 接口前缀 | `/api/v1` |
| 服务域名 | `https://api.ai-study.example.com` |
| 时间格式 | ISO 8601：`2026-09-22T09:00:00+08:00` |
| ID 类型 | 全部使用 `BIGINT UNSIGNED`，前端按字符串处理 |
| 布尔字段 | `0` 否 / `1` 是，响应统一使用整型 |

### 2.2 通用请求 Header

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | ✅ | 格式：`Bearer {access_token}` |
| `X-Device-Id` | 🟡 | 客户端设备 ID，用于多端登录控制与状态感知 |
| `X-Client-Type` | 🟡 | `web` / `h5` / `pc` |
| `X-App-Version` | 🟡 | 客户端版本，如 `1.0.0` |
| `Content-Type` | ✅ | `application/json; charset=UTF-8` |
| `X-Request-Id` | 🟡 | 客户端生成的请求唯一 ID，便于链路追踪 |

### 2.3 通用响应结构

```json
{
  "code": 200,
  "msg": "success",
  "trace_id": "a1b2c3d4-2026-09-22-09-00-00-001",
  "data": { /* 业务数据，结构随接口变化 */ },
  "timestamp": 1726966800
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，`200` 表示成功，其他参考错误码表 |
| `msg` | string | 文案，调试时可读 |
| `trace_id` | string | 服务端追踪 ID，反馈问题时提供 |
| `data` | object/array/null | 业务载荷 |
| `timestamp` | int | 服务端响应时间戳（秒） |

### 2.4 分页响应结构

列表接口统一采用：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [ ... ],
    "total": 123,
    "page": 1,
    "page_size": 20,
    "has_more": true
  }
}
```

分页请求参数：`page`（默认 1）、`page_size`（默认 20，最大 100）、`keyword`（可选）。

### 2.5 HTTP 状态码

| 状态码 | 含义 |
| --- | --- |
| 200 / 201 / 204 | 成功 / 创建成功 / 删除成功 |
| 400 | 请求参数错误 |
| 401 / 403 | 未登录 / 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如重复加入） |
| 429 | 请求过于频繁（限流） |
| 500 / 503 | 服务器错误 / 服务降级 |

### 2.6 业务错误码

| 错误码 | 含义 | 建议处理 |
| --- | --- | --- |
| 200 | 成功 | - |
| 1001 | 参数校验失败 | 检查请求参数 |
| 1002 / 1003 | Token 无效 / 过期 | 重新登录或刷新 |
| 1004 / 1005 | 权限不足 / 资源不存在 | 提示用户 |
| 1006 | 操作过于频繁 | 稍后重试 |
| 1010 | 二维码已过期或不存在 | 刷新二维码重新扫码（v1.2 新增） |
| 1011 | 短信验证码错误或已过期 | 重新获取验证码 |
| 1012 | 账号已被禁用 | 提示用户联系管理员 |
| 2001 | 课程未开通 | 引导开通 |
| 2003 | 作业已截止 | 提示截止 |
| 2004 | 错题已解决 | 不再展示 |
| 3001 | AI 调用失败 | 触发兜底话术 |
| 3002 | RAG 检索无结果 | 退化为通用回答 |
| 3003 | 引导式辅导超阶段 | 提示已超最大引导轮次 |
| 3004 | TTS 合成失败 | 重试或切换文字 |
| 9999 | 系统未知异常 | 联系客服 |

---

## 3. 认证与用户

> **对接表**：`sys_user`  
> **覆盖需求**：登录鉴权（全功能前置）、个人信息维护

### 3.1 账号密码登录  🔒 📌

- **接口**：`POST /api/v1/auth/login`

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | ✅ | 用户名 / 学号 / 工号 |
| `password` | string | ✅ | 密码；当前版本经 HTTPS 传输、后端 BCrypt 校验，文档早期规划的前端 RSA 加密暂未实现 |
| `loginType` | int | ✅ | 1=学号密码 2=工号密码 3=手机号验证码 4=扫码登录（演示环境为模拟扫码，见 3.6~3.9） |
| `deviceId` | string | 🟡 | 设备唯一标识 |

**响应 data**

```json
{
  "accessToken": "eyJ0eXAiOiJKV1Qi...",
  "refreshToken": "rt_20260922xxxxxxxxxxxx",
  "expiresIn": 7200,
  "tokenType": "Bearer",
  "user": {
    "id": 10001,
    "username": "2024100236",
    "nickname": "罗鸢",
    "realName": "罗鸢",
    "avatarUrl": "https://cdn.example.com/u/10001.png",
    "role": 1,
    "roleName": "student",
    "phone": "15608190106",
    "email": null
  }
}
```

> 字段命名与后端实际输出一致（驼峰）。早期规划的 `school_id`、`college`、`major`、`grade_year` 等字段当前版本未实现，`role` 为数字角色、`roleName` 为前端跳转用的字符串角色。

### 3.2 刷新令牌  🔒

- **接口**：`POST /api/v1/auth/refresh`
- **请求参数**：`refreshToken`
- **响应 data**：同 3.1 的令牌结构（驼峰字段，不含 user）。

### 3.3 退出登录  🔒

- **接口**：`POST /api/v1/auth/logout`
- **响应**：`data` 为 `null`

### 3.4 获取当前登录用户信息  🔒 📌

- **接口**：`GET /api/v1/auth/me`
- **说明**：前端启动时拉取，用于恢复登录态与角色判断。

**响应 data**

```json
{
  "id": 10001,
  "username": "2024100236",
  "nickname": "罗鸢",
  "realName": "罗鸢",
  "avatarUrl": "https://cdn.example.com/u/10001.png",
  "role": 1,
  "roleName": "student",
  "phone": "15608190106",
  "email": null
}
```

> 早期规划的 `permission_tags`、`feature_flags`、`school_id`、`college`、`major`、`grade_year` 当前版本未实现，后续迭代再补。

### 3.5 更新个人信息（含修改密码）  🔒

- **接口**：`PATCH /api/v1/users/me`
- **说明**：资料与密码合并为一个接口；传 `oldPassword` + `newPassword` 即改密，否则仅更新资料。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `realName` | string | 否 | 真实姓名（同时作为昵称展示） |
| `avatarUrl` | string | 否 | 头像 URL |
| `phone` / `email` | string | 否 | 联系方式 |
| `oldPassword` | string | 否 | 原密码（改密时必传） |
| `newPassword` | string | 否 | 新密码，8-20 位（改密时必传） |

### 3.6 生成登录二维码（免鉴权）  📌

> **v1.2 新增**。模拟扫码登录的入口。二维码会话为一次性凭证 `qr_token`，**服务端存内存/Redis（TTL 120s），不落库**。白名单账号配置于服务端 `qr.login.whitelist`（逗号分隔），校验发生在 3.8 扫码这一步。
> 生产上线时若接入真实 App/微信扫码，建议路径统一为 `/api/v1/auth/qr/*`，本组接口路径为当前后端实现的实际路径。

- **接口**：`POST /qr/create`

**请求参数**：无

**响应 data**

```json
{
  "qrToken": "9f8e7d6c5b4a3210fedcba9876543210",
  "expireSeconds": 120
}
```

### 3.7 轮询二维码状态（免鉴权）  📌

> **v1.2 新增**。PC 端每 1.5s 调用一次。登录成功时响应结构与 3.1 登录接口一致（token / username / id / role），前端可直接复用登录逻辑。

- **接口**：`GET /qr/status?qrToken={qrToken}`

**响应 data（status 状态机）**

| status | 含义 | 说明 |
| --- | --- | --- |
| 0 | 待扫码 | 二维码有效 |
| 1 | 已扫码待确认 | 手机端已扫码，等待确认 |
| 2 | 已登录 | 成功，data 中携带 `token / username / id / role` |
| 3 | 已过期 | 超时（120s）或不存在，前端应刷新二维码（对应错误码 1010） |
| 4 | 已取消 | 手机端主动取消 |
| 5 | 已拒绝 | 扫码账号不在白名单（对应错误码 1004） |

```json
{ "status": 2, "statusDesc": "已登录", "token": "eyJhbGciOi...", "username": "2024100236", "id": 10001, "role": "student" }
```

### 3.8 模拟扫码（免鉴权，演示环境专用）  🟡

> **v1.2 新增**。模拟"手机扫码"动作：服务端在此步做**白名单校验** + 用户存在性校验。白名单拒绝时状态机置 5，返回 `code=403`（对应错误码 1004）；账号在白名单但 `sys_user` 无记录时同样拒绝。**生产环境中本接口由真实扫码端调用替代，不存在模拟语义。**

- **接口**：`POST /qr/scan`

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `qrToken` | string | ✅ | 3.6 返回的二维码凭证 |
| `account` | string | ✅ | 扫码账号（学号/工号），须在白名单内 |

**响应**

| 场景 | code | msg |
| --- | --- | --- |
| 扫码成功 | 200 | 扫码成功，请在手机上确认登录 |
| 非白名单 / 用户不存在 | 403（1004） | 该账号不在登录白名单，无法扫码登录 |
| 二维码过期/状态错误 | 400（1010） | 二维码不存在或已过期，请刷新 |

### 3.9 扫码确认 / 取消（免鉴权）  🟡

> **v1.2 新增**。手机端的两步收尾动作；确认成功后签发 JWT 并把状态置 2，PC 端在 3.7 轮询中取得 token 完成登录。

- **确认**：`POST /qr/confirm`，参数 `qrToken`，成功响应 `{code:200, msg:"登录成功"}`
- **取消**：`POST /qr/cancel`，参数 `qrToken`，状态置 4
- **候选身份列表（仅 dev 环境）**：`GET /qr/mock-users`，返回模拟手机的候选账号及 `inWhitelist` 标记。**此接口仅存在于开发环境，生产包中必须移除。**

**安全约束**：① `qr_token` 一次性、120s 过期；② 扫码与确认必须来自同一会话上下文；③ `mock-users`、`/qr/scan` 的模拟语义不得进入生产构建。

### 3.10 账号注册（免鉴权）

- **接口**：`POST /api/v1/auth/register`
- **说明**：注册成功即签发令牌，前端无需再调登录接口。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userNo` | string | ✅ | 登录账号（学号 / 工号），全系统唯一，最长 32 位 |
| `password` | string | ✅ | 密码，8-20 位，后端 BCrypt 存储 |
| `realName` | string | ✅ | 真实姓名，最长 50 位 |
| `gender` | int | 否 | 0 未知，1 男，2 女 |
| `role` | int | 否 | 1 学生，2 教师；缺省为 1，教师账号建议由后台创建 |
| `phone` | string | 否 | 手机号，填写则需符合 `1[3-9]` 开头的 11 位格式 |

**响应 data**：同 3.1 的令牌结构（`accessToken` / `refreshToken` / `expiresIn` / `tokenType` / `user`）。账号重复时返回 1004 或 1005 错误码并提示。

### 3.11 发送短信验证码（免鉴权）

- **接口**：`POST /api/v1/auth/sms/code`
- **说明**：对接阿里云号码认证，验证码由云端生成与校验；同一手机号 60 秒内重复请求返回 1006。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `phone` | string | ✅ | 11 位大陆手机号 |

**响应 data**

```json
{
  "phone": "15608190106",
  "expireSeconds": 300,
  "tip": "验证码已发送，5分钟内有效"
}
```

### 3.12 手机验证码登录（免鉴权）

- **接口**：`POST /api/v1/auth/sms/login`
- **说明**：验证码错误或过期返回 1011；手机号不存在时自动创建学生账号（`userNo=M+手机号`，`phone_verified=1`，role=1）。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `phone` | string | ✅ | 11 位大陆手机号 |
| `verifyCode` | string | ✅ | 收到的 6 位验证码 |
| `deviceId` | string | 否 | 设备唯一标识 |

**响应 data**：同 3.1 的令牌结构。

---

## 4. 课程与知识库

> **对接表**：`course`、`chapter`、`knowledge_point`、`knowledge_document`  
> **覆盖需求**：FR-02 课程知识智能导学（学习路径、知识点拆解）、FR-09 自定义课程导入  
> **删减说明**：课程/章节/知识点的增删改统一收敛到教师后台（FR-10），学生侧只读。

### 4.1 课程列表（学生端）  🔒 📌

- **接口**：`GET /api/v1/courses`
- **Query**：`subject_type`（1=数学 2=语文 3=英语 4=物理 5=化学 6=其他）、`course_category`（1=公共课 2=专业课 3=选修课）

**响应 data.list**

```json
{
  "id": 201,
  "course_code": "MATH2401",
  "course_name": "高等数学 A（上）",
  "college": "信息工程学院",
  "credit": 5.0,
  "course_category": 1,
  "subject_type": 1,
  "cover_url": "https://cdn.example.com/c/201.png",
  "intro": "本课程主要讲述极限、导数与微积分...",
  "teacher": { "id": 20001, "nickname": "王老师", "avatar_url": "..." },
  "class_id": 301,
  "chapter_count": 48,
  "kp_count": 320
}
```

### 4.2 课程详情  🔒

- **接口**：`GET /api/v1/courses/{course_id}`
- **响应 data**：课程基础信息 + 教师信息 + 当前学生学习进度统计。

### 4.3 自定义课程导入  🔒 🟡

- **接口**：`POST /api/v1/courses`
- **覆盖需求**：FR-09 多学科全覆盖 —— 自定义课程导入。
- **说明**：学生可导入校内未对接的课程（自建课程），上传教材后走 4.6 文档解析流程构建知识库。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `course_name` | string | ✅ | 课程名 |
| `subject_type` | int | ✅ | 学科类型 |
| `college` | string | 否 | 学院 |
| `credit` | decimal | 否 | 学分 |
| `intro` | text | 否 | 课程简介 |

### 4.4 章节树（学习路径骨架）  🔒 📌

- **接口**：`GET /api/v1/courses/{course_id}/chapters`
- **覆盖需求**：FR-02 学习路径 —— 返回课程章节树，按 `sort_order` 升序，含建议学习时长。

**响应 data**

```json
{
  "list": [
    {
      "id": 501,
      "chapter_no": "1",
      "chapter_name": "函数与极限",
      "parent_id": 0,
      "sort_order": 1,
      "suggest_minutes": 180,
      "children": [
        { "id": 502, "chapter_no": "1.1", "chapter_name": "函数的性质", "parent_id": 501, "sort_order": 1 }
      ]
    }
  ]
}
```

### 4.5 知识点列表（知识点拆解）  🔒 📌

- **接口**：`GET /api/v1/chapters/{chapter_id}/knowledge-points`
- **覆盖需求**：FR-02 知识点拆解 —— 每个知识点带重点/难点/考点/易错点标记与个人掌握度。
- **Query**：`is_key`、`is_hard`、`is_exam_point`、`is_error_prone`

**响应 data.list**

```json
{
  "id": 801,
  "kp_code": "KP-MATH-1-1-1",
  "kp_name": "极限的定义",
  "is_key": 1,
  "is_hard": 0,
  "is_exam_point": 1,
  "is_error_prone": 1,
  "importance": 5,
  "mastery_threshold": 80,
  "mastery_score": 65
}
```

### 4.6 课程知识点聚合（导学总览）  🔒

- **接口**：`GET /api/v1/courses/{course_id}/knowledge-points/summary`
- **说明**：按章节聚合的知识点统计，用于数字人"知识导学"生成整课学习路径。

**响应 data**

```json
{
  "course_id": 201,
  "total": 320,
  "key_count": 80,
  "hard_count": 45,
  "exam_point_count": 220,
  "error_prone_count": 60,
  "chapters": [
    { "chapter_id": 501, "chapter_name": "函数与极限", "kp_count": 25, "key_count": 8 }
  ]
}
```

### 4.7 知识库文档上传  🔒

- **接口**：`POST /api/v1/courses/{course_id}/documents`
- **说明**：上传教材 / 课件 / 习题册，返回文档 ID 后触发解析（4.8）。

**请求参数**（multipart/form-data）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | binary | ✅ | 文件二进制 |
| `doc_name` | string | ✅ | 文档名 |
| `doc_type` | int | ✅ | 1=教材 2=课件 3=习题 4=试卷 5=补充 |
| `chapter_id` | int | 否 | 关联章节 |

### 4.8 触发文档解析（RAG 入库）  🔒

- **接口**：`POST /api/v1/documents/{document_id}/parse`
- **说明**：OCR/解析 + 切片 + 向量入库，异步任务。

**响应 data**：`{ "task_id": "TASK20260922001", "estimated_seconds": 60 }`

### 4.9 异步任务进度查询（通用）  🔒

- **接口**：`GET /api/v1/tasks/{task_id}`
- **说明**：文档解析、课件解析、报告生成、AI 出题等所有异步任务复用此接口。
- **响应 data**：`{ "status": 2, "progress": 80, "msg": "向量入库中..." }`（status：0=待处理 1=处理中 2=成功 3=失败）

### 4.10 文档列表  🔒

- **接口**：`GET /api/v1/courses/{course_id}/documents`
- **Query**：`doc_type`、`audit_status`、`keyword`

---

## 5. 数字人伴侣（FR-01）

> **对接表**：`digital_human_config`  
> **覆盖需求**：FR-01 数字人学习陪伴 —— 统一入口、形象自定义、语音文字动作、情绪反馈、状态感知

### 5.1 获取数字人配置  🔒 📌

- **接口**：`GET /api/v1/digital-human/config`
- **说明**：前端据此渲染数字人形象、TTS 音色与默认动作，是**统一入口**的渲染基础。

**响应 data**

```json
{
  "id": 1,
  "config_name": "高数小老师",
  "avatar_style": 1,
  "avatar_image_url": "https://cdn.example.com/avatar/math.png",
  "voice_id": "female_warm",
  "tone_style": 1,
  "speech_rate": 1.0,
  "action_config": { "default_gesture": "wave", "blink_ms": 4000 },
  "subject_style": "math",
  "is_default": 1
}
```

### 5.2 更新数字人配置（形象自定义）  🔒 📌

- **接口**：`PUT /api/v1/digital-human/config`
- **覆盖需求**：FR-01 形象自定义 —— 学生自主调整形象、音色、语气、语速。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `config_name` | string | 否 | 配置名 |
| `avatar_style` | int | 否 | 形象风格：1=学识型 2=活泼型 3=沉稳型 |
| `avatar_image_url` | string | 否 | 自定义形象资源 URL |
| `voice_id` | string | 否 | TTS 音色 ID |
| `tone_style` | int | 否 | 语气：1=温和 2=幽默 3=严谨 |
| `speech_rate` | decimal | 否 | 语速 0.50~2.00 |
| `action_config` | json | 否 | 动作与触发配置 |
| `subject_style` | string | 否 | 学科风格 |

### 5.3 数字人欢迎语（状态感知）  🔒 📌

- **接口**：`GET /api/v1/digital-human/greeting`
- **覆盖需求**：FR-01 状态感知 —— 结合学习状态（今日任务、连击天数、疲劳度）生成个性化欢迎语与开场动作。
- **Query**：`scene_type`（1=登录后 2=进入学习 3=作业前）

**响应 data**

```json
{
  "text": "罗鸢同学早上好，昨天的高数错题还剩 2 道没巩固，要不要现在开始？",
  "audio_url": "https://cdn.../greet.mp3",
  "duration_ms": 4200,
  "action": "wave",
  "state_hints": { "unfinished_tasks": 2, "streak_days": 28, "fatigue_level": "low" }
}
```

> 情绪反馈（FR-01）通过 6.5 消息点赞/点踩接口实现；语音文字动作通过 6.2 流式消息（含 `audio_url`）与 5.2 `action_config` 实现，不再单设接口。

---

## 6. 聊天与会话（FR-02 / FR-03）

> **对接表**：`chat_session`、`chat_message`  
> **覆盖需求**：FR-02 数字人讲解、FR-03 AI 智能答疑 —— 自然语言提问、拍照搜题、引导式作答、多轮追问  
> **删减说明**：删除非流式发消息与重新生成接口，统一走流式通道。

### 6.1 创建会话  🔒 📌

- **接口**：`POST /api/v1/chat/sessions`

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `course_id` | int | ✅ | 课程 ID |
| `kp_id` | int | 否 | 知识点 ID（导学/答疑场景） |
| `scene_type` | int | ✅ | 1=课前预习 2=课中伴学 3=作业辅导 4=课后复习 5=日常陪伴 |
| `title` | string | 否 | 会话标题，留空自动生成 |

**响应 data**

```json
{
  "session_id": 9001,
  "session_no": "SS202609220001",
  "scene_type": 3,
  "title": "作业辅导-极限求解",
  "status": 1,
  "created_at": "2026-09-22T09:10:00+08:00"
}
```

### 6.2 发送消息（流式，文字/语音/图片统一）  🔒 📌

- **接口**：`POST /api/v1/chat/sessions/{session_id}/messages/stream`
- **覆盖需求**：FR-03 全部四项能力：
  - 自然语言提问 → `msg_type=1`
  - 拍照搜题 → `msg_type=3`（图片经 15.3 OCR 识别后自动进入答疑流程）
  - 引导式作答 → `guide_stage` 阶段控制（思路→步骤→提示→总结）
  - 多轮追问 → 同一 session 内连续发送
- **响应**：SSE 流式。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | ✅ | 用户消息内容（语音时为 ASR 转写文本） |
| `msg_type` | int | ✅ | 1=文本 2=语音 3=图片 4=文件 |
| `media_url` | string | 否 | 语音/图片/文件 URL |
| `guide_stage` | int | 否 | 续接引导阶段：1=思路 2=步骤 3=提示 4=总结 |
| `client_msg_id` | string | ✅ | 客户端消息 ID（幂等） |

**SSE 事件流**

```
event: start
data: {"message_id": 9101, "guide_stage": 1}

event: delta
data: {"delta": "我们先来看这道题的关键是..."}

event: citation
data: {"doc_id": 1101, "page": 23, "snippet": "..."}

event: stage_change
data: {"from": 1, "to": 2}

event: done
data: {"message_id": 9101, "guide_stage": 2, "audio_url": "https://cdn.../reply.mp3", "reply_duration_ms": 4200}
```

### 6.3 会话列表  🔒

- **接口**：`GET /api/v1/chat/sessions`
- **Query**：`course_id`、`scene_type`、`status`、`page`、`page_size`

**响应 data.list**

```json
{
  "session_id": 9001,
  "session_no": "SS202609220001",
  "course_id": 201,
  "kp_id": 801,
  "scene_type": 3,
  "title": "作业辅导-极限求解",
  "status": 1,
  "message_count": 18,
  "last_message_at": "2026-09-22T09:40:00+08:00"
}
```

### 6.4 会话详情（消息历史）  🔒

- **接口**：`GET /api/v1/chat/sessions/{session_id}`
- **Query**：`page`、`page_size`

**消息对象结构**

```json
{
  "id": 9101,
  "sender_role": 2,
  "msg_type": 1,
  "content": "我们先来看这道题...",
  "media_url": null,
  "cited_sources": [{"doc_id": 1101, "page": 23, "snippet": "..."}],
  "guide_stage": 2,
  "feedback": 0,
  "reply_duration_ms": 4200,
  "create_time": "2026-09-22T09:30:00+08:00"
}
```

### 6.5 消息反馈（情绪反馈）  🔒 📌

- **接口**：`POST /api/v1/chat/messages/{message_id}/feedback`
- **覆盖需求**：FR-01 情绪反馈 —— 点赞/点踩驱动数字人语气与策略调整。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `feedback` | int | ✅ | 1=有用 2=没用 |
| `reason` | string | 否 | 反馈原因 |

### 6.6 结束会话  🔒

- **接口**：`POST /api/v1/chat/sessions/{session_id}/close`

---

## 7. 作业与题库（FR-04）

> **对接表**：`question`、`homework`、`homework_question`、`homework_submission`  
> **覆盖需求**：FR-04 作业与习题辅导 —— 作业智能分析、错题本、同类题推荐、完成提醒  
> **删减说明**：作业批阅（教师侧）见 7.10；错题本拆到第 8 章；完成提醒见第 13 章通知。

### 7.1 题目详情  🔒

- **接口**：`GET /api/v1/questions/{question_id}`
- **说明**：学生答题过程中按需拉取单题（题干、选项、答案在提交前不返回 `answer`/`analysis`）。

**响应 data**

```json
{
  "id": 5001,
  "course_id": 201,
  "kp_id": 801,
  "question_type": 6,
  "difficulty": 3,
  "content": "求 $\\lim_{x \\to 0} \\frac{\\sin x}{x}$ ...",
  "options": null,
  "source_desc": "同济高数上册 P52 第 3 题",
  "estimated_minutes": 5
}
```

### 7.2 同类题推荐  🔒 📌

- **接口**：`GET /api/v1/questions/{question_id}/similar`
- **覆盖需求**：FR-04 同类题推荐 —— 基于知识点 + 难度 + 来源推荐。
- **Query**：`limit`（默认 5）

**响应 data**

```json
{ "list": [ { "question_id": 5030, "difficulty": 3, "kp_name": "极限运算" } ] }
```

### 7.3 AI 智能出题  🔒 🟡

- **接口**：`POST /api/v1/questions/ai-generate`
- **覆盖需求**：FR-07 押题训练（题目生成部分）；亦服务于 FR-04 作业智能分析的组题。
- **响应 data**：`{ "task_id": "TASK20260922002" }`（结果经 4.9 任务接口轮询）

**请求参数**

```json
{
  "course_id": 201,
  "kp_ids": [801, 802],
  "question_type": 6,
  "difficulty": 3,
  "count": 5,
  "prompt": "围绕洛必达法则出 5 道计算题"
}
```

### 7.4 学生作业列表  🔒 📌

- **接口**：`GET /api/v1/homeworks`
- **覆盖需求**：FR-04 作业智能分析入口。
- **Query**：`course_id`、`status`（0=全部 1=进行中 2=已截止）、`homework_type`

**响应 data.list**

```json
{
  "homework_id": 7001,
  "course_id": 201,
  "title": "第三章函数与极限 作业",
  "homework_type": 1,
  "publish_at": "2026-09-21T18:00:00+08:00",
  "deadline": "2026-09-28T23:59:59+08:00",
  "estimated_minutes": 90,
  "allow_late": 1,
  "status": 1,
  "question_count": 10,
  "submission_status": 1,
  "total_score": 92.5
}
```

### 7.5 作业详情（含题目与智能分析）  🔒 📌

- **接口**：`GET /api/v1/homeworks/{homework_id}`
- **覆盖需求**：FR-04 作业智能分析 —— 返回难度分布、预计用时、涉及知识点与个人薄弱项提示。

**响应 data**

```json
{
  "id": 7001,
  "title": "第三章函数与极限 作业",
  "description": "...",
  "deadline": "2026-09-28T23:59:59+08:00",
  "allow_late": 1,
  "course": { "id": 201, "course_name": "高等数学 A（上）" },
  "questions": [
    { "question_id": 5001, "sort_order": 1, "score": 10, "require_guide": 1 }
  ],
  "analysis": {
    "difficulty_distribution": { "1": 2, "2": 5, "3": 3 },
    "involved_kps": [801, 802],
    "weak_hint": "你在「极限运算」掌握度 45，建议优先完成第 3-5 题"
  }
}
```

### 7.6 开始作答  🔒

- **接口**：`POST /api/v1/homeworks/{homework_id}/start`
- **响应 data**：`{ "submission_id": 8001, "start_at": "..." }`

### 7.7 提交单题答案  🔒

- **接口**：`POST /api/v1/homeworks/submissions/{submission_id}/answers`
- **说明**：逐题提交，即时判分客观题并写入答题记录（错题自动进错题本）。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `question_id` | int | ✅ | 题目 ID |
| `user_answer` | string | ✅ | 学生答案（LaTeX/文本） |
| `used_seconds` | int | 🟡 | 用时（秒） |
| `guide_rounds` | int | 🟡 | 引导轮次 |
| `is_final` | int | ✅ | 0=暂存 1=最终提交 |

**响应 data**：`{ "is_correct": 0, "analysis": "..." }`（仅 `is_final=1` 时返回判分）

### 7.8 提交整份作业  🔒 📌

- **接口**：`POST /api/v1/homeworks/submissions/{submission_id}/submit`
- **响应 data**：`{ "submission_id": 8001, "objective_score": 60, "grade_status": 1, "ai_comment": "..." }`

### 7.9 作业提交记录查询  🔒

- **接口**：`GET /api/v1/homeworks/{homework_id}/submission`
- **说明**：当前用户对该作业的提交记录、得分与批阅状态。

### 7.10 教师批改  🔒 🟢

- **接口**：`PATCH /api/v1/homeworks/submissions/{submission_id}/grade`
- **覆盖需求**：FR-10 作业批阅。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `subjective_score` | decimal | 🟡 | 主观题得分 |
| `total_score` | decimal | 🟡 | 总分（不填自动累加） |
| `teacher_comment` | string | 🟡 | 评语 |

### 7.11 教师发布作业  🔒 🟢

- **接口**：`POST /api/v1/homeworks`
- **覆盖需求**：FR-10 作业发布。
- **说明**：创建即发布（`publish` 合并进创建动作），支持从题库选题或 AI 组卷。

**请求参数**

```json
{
  "course_id": 201,
  "class_id": 301,
  "title": "第三章函数与极限 作业",
  "deadline": "2026-09-28T23:59:59+08:00",
  "allow_late": 1,
  "questions": [
    { "question_id": 5001, "score": 10, "sort_order": 1, "require_guide": 1 }
  ]
}
```

---

## 8. 错题本（FR-04）

> **对接表**：`wrong_book`、`answer_record`  
> **覆盖需求**：FR-04 错题本  
> **删减说明**：删除手动加入错题与错题重练接口 —— 错题由答题记录自动汇总（7.7），重练由同类题推荐（7.2）覆盖。

### 8.1 错题列表  🔒 📌

- **接口**：`GET /api/v1/wrong-book`
- **Query**：`course_id`、`mastery_status`（0=未掌握 1=复习中 2=已掌握）

**响应 data.list**

```json
{
  "id": 12001,
  "question_id": 5001,
  "question_content": "求 $\\lim_{x \\to 0} \\frac{\\sin x}{x}$ ...",
  "kp_id": 801,
  "kp_name": "极限的定义",
  "wrong_reason": "概念混淆",
  "wrong_count": 3,
  "mastery_status": 1,
  "last_wrong_at": "2026-09-21T21:00:00+08:00",
  "similar_question_ids": [5030, 5031, 5032]
}
```

### 8.2 标记错题为已掌握  🔒

- **接口**：`POST /api/v1/wrong-book/{id}/resolve`

### 8.3 错题本统计  🔒

- **接口**：`GET /api/v1/wrong-book/stats`
- **Query**：`course_id`

**响应 data**

```json
{
  "total": 38,
  "unresolved": 12,
  "reviewing": 20,
  "resolved": 6,
  "by_kp": [
    { "kp_id": 801, "kp_name": "极限的定义", "count": 5 },
    { "kp_id": 802, "kp_name": "极限运算", "count": 8 }
  ]
}
```

---

## 9. 预习复习与学习计划（FR-05）

> **对接表**：`study_plan`、`plan_task`  
> **覆盖需求**：FR-05 预习与复习规划 —— 预习清单、复习计划、记忆曲线、抽背默写速记  
> **删减说明**：删除任务跳过/提醒修改/进度手动更新 —— 进度由完成任务自动推进，提醒由通知中心统一发送。

### 9.1 学习计划列表  🔒

- **接口**：`GET /api/v1/study-plans`
- **Query**：`course_id`、`plan_type`（1=预习 2=复习 3=冲刺）、`status`

**响应 data.list**

```json
{
  "id": 15001,
  "title": "高数第三章预习计划",
  "plan_type": 1,
  "source_type": 1,
  "start_date": "2026-09-22",
  "end_date": "2026-09-28",
  "status": 1,
  "progress": 35,
  "item_count": 12,
  "finished_count": 4
}
```

### 9.2 创建学习计划（预习清单）  🔒 📌

- **接口**：`POST /api/v1/study-plans`
- **覆盖需求**：FR-05 预习清单 —— 按章节勾选知识点生成预习任务清单。

**请求参数**

```json
{
  "course_id": 201,
  "plan_type": 1,
  "title": "高数第三章预习计划",
  "start_date": "2026-09-22",
  "end_date": "2026-09-28",
  "kp_tasks": [
    { "kp_id": 801, "remind_at": "2026-09-22T20:00:00+08:00", "remind_content": "预习极限定义" }
  ]
}
```

### 9.3 计划详情（含任务清单）  🔒

- **接口**：`GET /api/v1/study-plans/{plan_id}`
- **说明**：任务项含记忆曲线阶段标记：1=当天 2=隔1天 3=隔2天 4=隔4天 5=隔7天 6=隔15天；`remind_content` 携带抽背/默写/速记类型标记。

**响应 data.plan_tasks**

```json
{
  "task_id": 16001,
  "kp_id": 801,
  "kp_name": "极限的定义",
  "stage": 1,
  "stage_label": "当天",
  "remind_at": "2026-09-22T20:00:00+08:00",
  "remind_content": "抽背：极限的 ε-δ 定义",
  "status": 0
}
```

### 9.4 今日任务  🔒 📌

- **接口**：`GET /api/v1/plan-tasks/today`
- **说明**：首页数字人状态感知（5.3）与待办列表共用的数据源。

### 9.5 完成任务  🔒

- **接口**：`POST /api/v1/plan-tasks/{task_id}/finish`
- **说明**：标记完成并自动更新所属计划进度。

### 9.6 AI 自动生成复习计划（记忆曲线）  🔒 📌

- **接口**：`POST /api/v1/study-plans/ai-generate`
- **覆盖需求**：FR-05 复习计划 + 记忆曲线 —— 基于错题本、掌握度与艾宾浩斯曲线自动编排。

**请求参数**

```json
{
  "course_id": 201,
  "plan_type": 2,
  "start_date": "2026-09-22",
  "end_date": "2026-10-22",
  "daily_minutes": 30
}
```

**响应 data**：`{ "plan_id": 15002, "preview": [ /* 计划预览 */ ] }`

### 9.7 考前冲刺计划  🔒 🟡

- **接口**：`POST /api/v1/study-plans/exam-sprint`
- **覆盖需求**：FR-07 备考冲刺服务的计划编排。
- **请求参数**：`exam_date`、`exam_paper_id`、`daily_minutes`

---

## 10. 学习统计与掌握度（FR-08）

> **对接表**：`study_stat`、`knowledge_mastery`  
> **覆盖需求**：FR-08 学习数据与成长报告 —— 学情统计、掌握度雷达图数据源  
> **删减说明**：删除掌握度详情与手动重算 —— 列表已含单知识点数据，重算由答题事件异步触发。

### 10.1 今日学情统计  🔒

- **接口**：`GET /api/v1/study-stats/today`

**响应 data**

```json
{
  "effective_minutes": 95,
  "qa_count": 23,
  "homework_count": 1,
  "exercise_count": 15,
  "correct_count": 11,
  "focus_score": 86,
  "review_finished": 3,
  "avg_answer_seconds": 45
}
```

### 10.2 每日学情统计（趋势）  🔒 📌

- **接口**：`GET /api/v1/study-stats/daily`
- **Query**：`start_date`、`end_date`、`course_id`

**响应 data.list**：与 10.1 同构，另含 `stat_date` 字段。

### 10.3 累计学情统计  🔒

- **接口**：`GET /api/v1/study-stats/total`
- **Query**：`course_id`（不传为全课程）

**响应 data**

```json
{
  "total_minutes": 12450,
  "total_qa": 1850,
  "total_homework": 32,
  "total_exercise": 980,
  "correct_rate": 78.5,
  "current_streak_days": 28
}
```

### 10.4 知识点掌握度列表（雷达图数据源）  🔒 📌

- **接口**：`GET /api/v1/mastery`
- **覆盖需求**：FR-08 掌握度雷达图 —— 按课程/学科聚合掌握度，前端渲染雷达图。
- **Query**：`course_id`、`weak_flag`

**响应 data.list**

```json
{
  "kp_id": 801,
  "kp_name": "极限的定义",
  "course_id": 201,
  "mastery_score": 65,
  "accuracy_rate": 72.5,
  "avg_answer_seconds": 60,
  "review_completion": 50,
  "exercise_total": 25,
  "weak_flag": 1
}
```

---

## 11. 成长报告（FR-08）

> **对接表**：`growth_report`  
> **覆盖需求**：FR-08 学情统计、掌握度雷达图、周月诊断建议  
> **删减说明**：删除任务进度接口（复用 4.9）、分享与家长查看接口（不在功能表内）。

### 11.1 报告列表  🔒

- **接口**：`GET /api/v1/growth-reports`
- **Query**：`report_type`（1=周报 2=月报）、`start_date`、`end_date`

### 11.2 报告详情  🔒 📌

- **接口**：`GET /api/v1/growth-reports/{report_id}`

**响应 data**

```json
{
  "id": 18001,
  "report_type": 1,
  "period_start": "2026-09-15",
  "period_end": "2026-09-21",
  "total_minutes": 580,
  "radar_data": {
    "专注力": 86,
    "坚持度": 90,
    "掌握度": 72,
    "作业完成": 95,
    "复习巩固": 68,
    "错题减少": 70
  },
  "weak_subjects": [
    { "subject_type": 1, "subject_name": "数学", "kp_names": ["极限运算", "导数应用"] }
  ],
  "trend_data": [
    { "date": "2026-09-15", "minutes": 70 },
    { "date": "2026-09-16", "minutes": 95 }
  ],
  "diagnosis": "本周学习时长稳定，但数学复习巩固不足，建议每日增加 20 分钟专项练习。",
  "suggestions": [
    "每天 20:00-20:30 复习 1 个薄弱知识点",
    "完成错题本中 3 道数学同类题"
  ]
}
```

### 11.3 生成报告  🔒

- **接口**：`POST /api/v1/growth-reports/generate`
- **响应 data**：`{ "report_id": 18001, "task_id": "TASK20260922003" }`（进度经 4.9 查询）

**请求参数**

```json
{
  "report_type": 1,
  "period_start": "2026-09-15",
  "period_end": "2026-09-21"
}
```

---

## 12. 课堂资料与备考（FR-06 / FR-07）

> **对接表**：`exam_paper`、`courseware`  
> **覆盖需求**：FR-06 课件解析、录音转写（实时追问走 6.2 流式消息）；FR-07 真题解析、考点串讲（押题训练出题走 7.3）  
> **删减说明**：删除试卷 CRUD（V2.0 教师后台统一维护）。

### 12.1 试卷列表  🔒 🟡

- **接口**：`GET /api/v1/exam-papers`
- **Query**：`course_id`、`paper_type`（1=历年真题 2=模拟卷 3=押题卷）、`year`

### 12.2 试卷详情（含真题解析）  🔒 🟡

- **接口**：`GET /api/v1/exam-papers/{paper_id}`
- **覆盖需求**：FR-07 真题解析 —— 返回试卷题目列表与逐题解析。

**响应 data**

```json
{
  "id": 22001,
  "paper_name": "2025 年高数 A 期末真题",
  "paper_type": 1,
  "year": 2025,
  "total_score": 100,
  "duration_minutes": 120,
  "analysis_url": "https://cdn.../analysis.pdf",
  "questions": [ { "question_id": 5001, "analysis": "...", "exam_kp": [801] } ]
}
```

### 12.3 试卷智能解析  🔒 🟡

- **接口**：`POST /api/v1/exam-papers/smart-parse`
- **覆盖需求**：FR-07 真题解析（入口）—— 上传试卷图片/PDF，OCR + AI 拆分题目入库。
- **请求参数**：`file_url` 或 multipart `file`、`course_id`、`paper_type`
- **响应 data**：`{ "paper_id": 22001, "task_id": "..." }`

### 12.4 上传课堂资料  🔒 🟡

- **接口**：`POST /api/v1/courses/{course_id}/courseware`
- **覆盖需求**：FR-06 课件解析 —— 上传 PPT / 教材 / 课堂录音。

**请求参数**（multipart/form-data）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | binary | ✅ | 文件 |
| `ware_type` | int | ✅ | 1=PPT 2=教材 3=课堂录音 4=讲义 |
| `title` | string | ✅ | 标题 |
| `chapter_id` | int | 否 | 关联章节 |
| `class_at` | string | 否 | 上课时间 |

### 12.5 触发课件解析（ASR 转写 + 重点提取）  🔒 🟡 📌

- **接口**：`POST /api/v1/courseware/{ware_id}/parse`
- **覆盖需求**：FR-06 课件解析、录音转写。
- **响应 data**：`{ "task_id": "...", "estimated_seconds": 90 }`

### 12.6 课件列表与解析结果  🔒 🟡

- **接口**：`GET /api/v1/courses/{course_id}/courseware`
- **Query**：`ware_type`、`chapter_id`
- **响应 data.list**（解析完成后含以下字段）

```json
{
  "id": 20001,
  "ware_type": 3,
  "title": "第3讲 函数极限",
  "file_url": "https://cdn.../recording.mp3",
  "duration_seconds": 2700,
  "parse_status": 2,
  "structure_note": "1. 极限定义 ...\n2. 极限运算 ...",
  "transcript_text": "同学们好，今天我们来看...",
  "key_points": ["极限定义", "极限运算法则", "两个重要极限"]
}
```

---

## 13. 通知与消息

> **对接表**：`notification`  
> **覆盖需求**：FR-04 完成提醒、FR-05 记忆曲线复习提醒（提醒统一走通知中心）  
> **删减说明**：删除通知详情/全部已读/通知设置 —— 列表已含内容，逐条已读足够，偏好设置并入用户资料。

### 13.1 通知列表  🔒

- **接口**：`GET /api/v1/notifications`
- **Query**：`biz_type`（1=作业截止 2=复习提醒 3=抽背提醒 4=学业预警 5=系统通知）、`read_status`

**响应 data.list**

```json
{
  "id": 30001,
  "biz_type": 1,
  "biz_id": 7001,
  "title": "作业即将截止",
  "content": "《高数第3章作业》还剩 2 小时截止，请尽快完成。",
  "read_status": 0,
  "send_at": "2026-09-22T09:00:00+08:00"
}
```

### 13.2 标记已读  🔒

- **接口**：`POST /api/v1/notifications/{id}/read`

### 13.3 未读数  🔒

- **接口**：`GET /api/v1/notifications/unread-count`
- **响应 data**：`{ "total": 5, "by_type": { "1": 2, "2": 1, "4": 2 } }`

---

## 14. 教师后台（FR-10）

> **对接表**：`sys_user`、`class_info`、`class_member`、`course`、`knowledge_point`、`question`  
> **覆盖需求**：FR-10 教师 / 管理后台 —— 知识库维护、作业发布批阅统计、班级监控预警  
> **删减说明**：班级/课程/知识点/题库的常规 CRUD 全部收敛为「批量导入 + 标签维护」两条维护通道；作业发布与批阅见 7.10 / 7.11。

### 14.1 教师工作台概览（班级监控预警）  🔒 🟢 📌

- **接口**：`GET /api/v1/teacher/dashboard`
- **覆盖需求**：FR-10 班级监控预警。

**响应 data**

```json
{
  "class_count": 4,
  "student_count": 156,
  "pending_grade_count": 23,
  "risk_students": [
    { "user_id": 10086, "nickname": "...", "risk_level": 3, "reason": "连续 3 天未学习" }
  ],
  "kp_weak_distribution": [
    { "kp_id": 801, "kp_name": "极限的定义", "weak_student_count": 28 }
  ]
}
```

### 14.2 班级学情分析  🔒 🟢

- **接口**：`GET /api/v1/teacher/classes/{class_id}/analysis`
- **Query**：`course_id`、`start_date`、`end_date`
- **响应 data**：班级平均学习时长、作业完成率、知识点掌握度分布（含班级维度掌握度，替代单独的班级掌握度接口）。

### 14.3 学员个人学情  🔒 🟢 📌

- **接口**：`GET /api/v1/teacher/students/{user_id}/profile`

**响应 data**

```json
{
  "user": { "id": 10086, "nickname": "...", "class_id": 301 },
  "today_minutes": 45,
  "week_minutes": 320,
  "mastery_summary": { "strong": 25, "medium": 30, "weak": 15 },
  "wrong_book_count": 12,
  "homework_completion_rate": 92,
  "risk_signals": [ "近 3 天日均学习时长 < 30 分钟", "极限章节错题率 > 50%" ]
}
```

### 14.4 作业统计  🔒 🟢 📌

- **接口**：`GET /api/v1/teacher/homeworks/{homework_id}/stats`
- **覆盖需求**：FR-10 作业统计（含整班提交情况，替代单独的提交列表接口）。

**响应 data**

```json
{
  "submitted": 45,
  "total": 50,
  "avg_score": 78.5,
  "max_score": 100,
  "min_score": 32,
  "top_errors": [
    { "question_id": 5001, "wrong_count": 18, "wrong_rate": 40 }
  ]
}
```

### 14.5 知识库维护 —— 批量导入  🔒 🟢

- **接口**：`POST /api/v1/teacher/knowledge-points/batch-import`
- **覆盖需求**：FR-10 知识库维护。
- **请求参数**：`course_id`、`chapter_id`、`items`（知识点数组，含重点/难点/考点/易错标记）

### 14.6 知识库维护 —— 标签调整  🔒 🟢

- **接口**：`PATCH /api/v1/teacher/knowledge-points/{kp_id}/tags`
- **请求参数**：`is_key`、`is_hard`、`is_exam_point`、`is_error_prone`、`importance`

### 14.7 题库维护 —— 批量导入  🔒 🟢

- **接口**：`POST /api/v1/teacher/questions/batch-import`
- **请求参数**：`course_id`、`questions`（题目数组，含题干/选项/答案/解析/知识点）

---

## 15. AI 能力接口

> **覆盖需求**：FR-01 语音（TTS/ASR）、FR-03 拍照搜题（OCR）  
> **删减说明**：删除 RAG 检索、引导式答疑直调、公式识别独立接口、AI 审计日志 —— RAG 与引导式答疑由 6.2 流式消息内部完成；公式识别并入 OCR 的 `mode` 参数；审计日志为内部合规功能。

### 15.1 TTS 语音合成  🔒

- **接口**：`POST /api/v1/ai/tts/synthesize`

**请求参数**

```json
{
  "text": "我们先看这道题...",
  "voice_id": "female_warm",
  "speech_rate": 1.0,
  "format": "mp3"
}
```

**响应 data**：`{ "audio_url": "https://cdn.../tts/xxx.mp3", "duration_ms": 4200 }`

### 15.2 ASR 语音识别  🔒

- **接口**：`POST /api/v1/ai/asr/recognize`
- **覆盖需求**：FR-01 语音输入、FR-06 实时追问（语音追问先转文字再走 6.2）。

**请求参数**：`audio_url` 或 multipart `file`、`format`

**响应 data**：`{ "text": "...", "duration_ms": 5200, "confidence": 0.96 }`

### 15.3 OCR 识别（题目/公式，含拍照搜题）  🔒 📌

- **接口**：`POST /api/v1/ai/ocr/recognize`
- **覆盖需求**：FR-03 拍照搜题 —— `mode=question` 时返回结构化题目并自动匹配题库。
- **请求参数**：`image_url` 或 multipart `file`、`mode`（`text`=普通文本 / `question`=题目结构化 / `formula`=数学公式转 LaTeX）

**响应 data（mode=question）**

```json
{
  "text": "求 lim(x→0) sinx/x 的值",
  "matched_question_id": 5001,
  "match_confidence": 0.94,
  "session_hint": { "scene_type": 3, "kp_id": 801 }
}
```

**响应 data（mode=formula）**：`{ "latex": "\\lim_{x \\to 0} \\frac{\\sin x}{x}" }`

---

## 16. 附录

### 16.1 全部字典（前端启动缓存）  🔒

- **接口**：`GET /api/v1/dicts/all`
- **说明**：一次性返回 `subject_type`、`scene_type`、`question_type`、`plan_type` 等全部枚举字典，替代逐个字典查询。

**响应 data**

```json
{
  "subject_type": [ { "value": 1, "label": "数学" }, { "value": 2, "label": "语文" } ],
  "scene_type": [ { "value": 1, "label": "课前预习" } ]
}
```

### 16.2 通用文件上传  🔒

- **接口**：`POST /api/v1/files/upload`
- **请求**：multipart/form-data，`file` 字段；`biz_type` 可选标注用途（`image`/`audio`/`doc`）。
- **响应 data**：`{ "file_url": "https://...", "file_id": "...", "file_size": 123456 }`

### 16.3 健康检查

- **接口**：`GET /api/v1/health`
- **响应**：`{ "status": "ok", "version": "1.1.0", "timestamp": 1726966800 }`

### 16.4 性能指标参考

| 指标 | 目标值 | 说明 |
| --- | --- | --- |
| 引导式首字节响应时间 | ≤ 3 秒 | 提交问题到返回第一段思路 |
| 日常追问响应时间 | ≤ 2 秒 | 课堂追问（FR-06） |
| 语音识别响应时间 | ≤ 3 秒 | 录音转文字 |
| 印刷体识别成功率 | ≥ 95% | 题目图片（FR-03） |
| 系统可用性 | ≥ 99.5% | 月度统计 |
| RAG 检索准确率 | ≥ 90% | 内部评测 |

### 16.5 状态机说明

**作业状态**：`草稿 →（发布）→ 已发布 →（截止）→ 已截止 →（归档）→ 已归档`

**错题掌握度**：`未掌握 →（练习答对）→ 复习中 →（再次答对）→ 已掌握`；`复习中 →（再错）→ 未掌握`

**引导式阶段**：`思路 →（要提示）→ 步骤 →（卡住）→ 提示 →（完成）→ 总结`

### 16.6 FR 功能覆盖对照表

| 需求 | 核心能力 | 支撑接口 |
| --- | --- | --- |
| FR-01 数字人学习陪伴 | 统一入口、形象自定义、语音文字动作、情绪反馈、状态感知 | 5.1 / 5.2 / 5.3 / 6.2 / 6.5 / 15.1 / 15.2 |
| FR-02 课程知识智能导学 | 学习路径、知识点拆解、数字人讲解 | 4.4 / 4.5 / 4.6 / 6.1 / 6.2 |
| FR-03 AI 智能答疑 | 自然语言提问、拍照搜题、引导式作答、多轮追问 | 6.1 / 6.2 / 6.4 / 15.3 |
| FR-04 作业与习题辅导 | 作业智能分析、错题本、同类题推荐、完成提醒 | 7.4 / 7.5 / 7.7 / 7.8 / 8.1~8.3 / 7.2 / 13.1 |
| FR-05 预习与复习规划 | 预习清单、复习计划、记忆曲线、抽背默写速记 | 9.1~9.6 / 13.1 |
| FR-06 课堂辅助能力 | 课件解析、录音转写、实时追问 | 12.4~12.6 / 6.2 / 15.2 |
| FR-07 备考冲刺服务 | 真题解析、考点串讲、押题训练、应试指导 | 12.1~12.3 / 7.3 / 9.7 |
| FR-08 学习数据与成长报告 | 学情统计、掌握度雷达图、周月诊断建议 | 10.1~10.4 / 11.1~11.3 |
| FR-09 多学科全覆盖 | 公共课、专业课适配、自定义课程导入 | 4.1（subject_type 筛选）/ 4.3 / 4.7 / 4.8 |
| FR-10 教师 / 管理后台 | 知识库维护、作业发布批阅统计、班级监控预警 | 14.1~14.7 / 7.10 / 7.11 |

### 16.7 接口清单汇总（86 个）

| 序号 | 模块 | 接口名称 | Method | Path | 版本 |
| --- | --- | --- | --- | --- | --- |
| 1 | 认证 | 登录 | POST | /api/v1/auth/login | ✅ |
| 2 | 认证 | 刷新令牌 | POST | /api/v1/auth/refresh | ✅ |
| 3 | 认证 | 退出登录 | POST | /api/v1/auth/logout | ✅ |
| 4 | 认证 | 当前用户信息 | GET | /api/v1/auth/me | ✅ |
| 5 | 用户 | 更新个人信息（含改密） | PATCH | /api/v1/users/me | ✅ |
| 6 | 课程 | 课程列表 | GET | /api/v1/courses | ✅ |
| 7 | 课程 | 课程详情 | GET | /api/v1/courses/{course_id} | ✅ |
| 8 | 课程 | 自定义课程导入 | POST | /api/v1/courses | 🟡 |
| 9 | 课程 | 章节树 | GET | /api/v1/courses/{course_id}/chapters | ✅ |
| 10 | 知识点 | 知识点列表 | GET | /api/v1/chapters/{chapter_id}/knowledge-points | ✅ |
| 11 | 知识点 | 课程知识点聚合 | GET | /api/v1/courses/{course_id}/knowledge-points/summary | ✅ |
| 12 | 知识库 | 文档上传 | POST | /api/v1/courses/{course_id}/documents | ✅ |
| 13 | 知识库 | 触发解析 | POST | /api/v1/documents/{document_id}/parse | ✅ |
| 14 | 通用 | 异步任务进度 | GET | /api/v1/tasks/{task_id} | ✅ |
| 15 | 知识库 | 文档列表 | GET | /api/v1/courses/{course_id}/documents | ✅ |
| 16 | 数字人 | 获取配置 | GET | /api/v1/digital-human/config | ✅ |
| 17 | 数字人 | 更新配置（形象自定义） | PUT | /api/v1/digital-human/config | ✅ |
| 18 | 数字人 | 欢迎语（状态感知） | GET | /api/v1/digital-human/greeting | ✅ |
| 19 | 聊天 | 创建会话 | POST | /api/v1/chat/sessions | ✅ |
| 20 | 聊天 | 流式发消息 | POST | /api/v1/chat/sessions/{session_id}/messages/stream | ✅ |
| 21 | 聊天 | 会话列表 | GET | /api/v1/chat/sessions | ✅ |
| 22 | 聊天 | 会话详情（历史） | GET | /api/v1/chat/sessions/{session_id} | ✅ |
| 23 | 聊天 | 消息反馈（情绪反馈） | POST | /api/v1/chat/messages/{message_id}/feedback | ✅ |
| 24 | 聊天 | 结束会话 | POST | /api/v1/chat/sessions/{session_id}/close | ✅ |
| 25 | 题库 | 题目详情 | GET | /api/v1/questions/{question_id} | ✅ |
| 26 | 题库 | 同类题推荐 | GET | /api/v1/questions/{question_id}/similar | ✅ |
| 27 | 题库 | AI 智能出题 | POST | /api/v1/questions/ai-generate | 🟡 |
| 28 | 作业 | 学生作业列表 | GET | /api/v1/homeworks | ✅ |
| 29 | 作业 | 作业详情（智能分析） | GET | /api/v1/homeworks/{homework_id} | ✅ |
| 30 | 作业 | 开始作答 | POST | /api/v1/homeworks/{homework_id}/start | ✅ |
| 31 | 作业 | 提交单题 | POST | /api/v1/homeworks/submissions/{submission_id}/answers | ✅ |
| 32 | 作业 | 提交整份 | POST | /api/v1/homeworks/submissions/{submission_id}/submit | ✅ |
| 33 | 作业 | 提交记录查询 | GET | /api/v1/homeworks/{homework_id}/submission | ✅ |
| 34 | 作业 | 教师批改 | PATCH | /api/v1/homeworks/submissions/{submission_id}/grade | 🟢 |
| 35 | 作业 | 教师发布作业 | POST | /api/v1/homeworks | 🟢 |
| 36 | 错题 | 错题列表 | GET | /api/v1/wrong-book | ✅ |
| 37 | 错题 | 标记已掌握 | POST | /api/v1/wrong-book/{id}/resolve | ✅ |
| 38 | 错题 | 错题统计 | GET | /api/v1/wrong-book/stats | ✅ |
| 39 | 计划 | 计划列表 | GET | /api/v1/study-plans | ✅ |
| 40 | 计划 | 创建计划（预习清单） | POST | /api/v1/study-plans | ✅ |
| 41 | 计划 | 计划详情（含任务） | GET | /api/v1/study-plans/{plan_id} | ✅ |
| 42 | 计划 | 今日任务 | GET | /api/v1/plan-tasks/today | ✅ |
| 43 | 计划 | 完成任务 | POST | /api/v1/plan-tasks/{task_id}/finish | ✅ |
| 44 | 计划 | AI 复习计划（记忆曲线） | POST | /api/v1/study-plans/ai-generate | ✅ |
| 45 | 计划 | 考前冲刺计划 | POST | /api/v1/study-plans/exam-sprint | 🟡 |
| 46 | 统计 | 今日学情 | GET | /api/v1/study-stats/today | ✅ |
| 47 | 统计 | 每日学情（趋势） | GET | /api/v1/study-stats/daily | ✅ |
| 48 | 统计 | 累计学情 | GET | /api/v1/study-stats/total | ✅ |
| 49 | 统计 | 掌握度列表（雷达图） | GET | /api/v1/mastery | ✅ |
| 50 | 报告 | 报告列表 | GET | /api/v1/growth-reports | ✅ |
| 51 | 报告 | 报告详情 | GET | /api/v1/growth-reports/{report_id} | ✅ |
| 52 | 报告 | 生成报告 | POST | /api/v1/growth-reports/generate | ✅ |
| 53 | 试卷 | 试卷列表 | GET | /api/v1/exam-papers | 🟡 |
| 54 | 试卷 | 试卷详情（真题解析） | GET | /api/v1/exam-papers/{paper_id} | 🟡 |
| 55 | 试卷 | 试卷智能解析 | POST | /api/v1/exam-papers/smart-parse | 🟡 |
| 56 | 课件 | 上传课堂资料 | POST | /api/v1/courses/{course_id}/courseware | 🟡 |
| 57 | 课件 | 触发解析（ASR） | POST | /api/v1/courseware/{ware_id}/parse | 🟡 |
| 58 | 课件 | 课件列表与解析结果 | GET | /api/v1/courses/{course_id}/courseware | 🟡 |
| 59 | 通知 | 通知列表 | GET | /api/v1/notifications | ✅ |
| 60 | 通知 | 标记已读 | POST | /api/v1/notifications/{id}/read | ✅ |
| 61 | 通知 | 未读数 | GET | /api/v1/notifications/unread-count | ✅ |
| 62 | 教师 | 工作台概览（预警） | GET | /api/v1/teacher/dashboard | 🟢 |
| 63 | 教师 | 班级学情分析 | GET | /api/v1/teacher/classes/{class_id}/analysis | 🟢 |
| 64 | 教师 | 学员个人学情 | GET | /api/v1/teacher/students/{user_id}/profile | 🟢 |
| 65 | 教师 | 作业统计 | GET | /api/v1/teacher/homeworks/{homework_id}/stats | 🟢 |
| 66 | 教师 | 知识点批量导入 | POST | /api/v1/teacher/knowledge-points/batch-import | 🟢 |
| 67 | 教师 | 知识点标签调整 | PATCH | /api/v1/teacher/knowledge-points/{kp_id}/tags | 🟢 |
| 68 | 教师 | 题目批量导入 | POST | /api/v1/teacher/questions/batch-import | 🟢 |
| 69 | AI | TTS 合成 | POST | /api/v1/ai/tts/synthesize | ✅ |
| 70 | AI | ASR 识别 | POST | /api/v1/ai/asr/recognize | ✅ |
| 71 | AI | OCR 识别（含拍照搜题） | POST | /api/v1/ai/ocr/recognize | ✅ |
| 72 | 通用 | 全部字典 | GET | /api/v1/dicts/all | ✅ |
| 73 | 通用 | 文件上传 | POST | /api/v1/files/upload | ✅ |
| 74 | 通用 | 健康检查 | GET | /api/v1/health | ✅ |
| 75 | 认证 | 账号注册 | POST | /api/v1/auth/register | ✅ |
| 76 | 认证 | 发送短信验证码 | POST | /api/v1/auth/sms/code | ✅ |
| 77 | 认证 | 手机验证码登录 | POST | /api/v1/auth/sms/login | ✅ |

> 汇总表内列 77 行；此外扫码登录接口组 6 个（`/qr/create`、`/qr/status`、`/qr/scan`、`/qr/confirm`、`/qr/cancel`、`/qr/mock-users`）路径无 `/api/v1` 前缀，单列于 3.6~3.9。合计可调用端点 **86** 个。

---

## 17. v1.3 架构扩展接口（服务拆分与 RAG）

> 本章对应架构调整：Java 模块化主服务 + Python AI 服务（FastAPI）+ MinIO + Qdrant，由 Nginx 统一入口。
> 通用规范（Result 包装、错误码、分页、Header）沿用第 2 章；本章只描述路径、参数与服务归属。
> 与 v1.1 接口的替代/并存关系见 17.8。

### 17.1 Nginx 路由规则

| 路径前缀 | 目标服务 | 说明 |
| --- | --- | --- |
| `/api/v1/ai/**` | Python AI 服务（:8000） | 流式对话等，Python 本地验 JWT |
| `/api/v1/**`（除 ai 外） | Java 主服务（:8080） | 业务、认证、知识库、文件 |
| `/qr/**` | Java 主服务 | 扫码登录 |
| `/internal/**` | 外部一律拒绝（404） | 仅 Docker 内部网络可达，见 17.6 |

### 17.2 知识库管理（Java 主服务）

#### 17.2.1 创建知识库  🔒

- **接口**：`POST /api/v1/kb`
- **权限**：登录用户；`visibility=2 公共库`仅教师/教务可创建（RBAC 拦截），学生仅可建私有库。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `kbName` | string | ✅ | 知识库名称 |
| `description` | string | 否 | 描述 |
| `visibility` | int | ✅ | 1 私有库，2 公共库，3 课程库 |
| `courseId` | string | 否 | 课程 ID；`visibility=3` 时必填 |

**响应 data**：知识库对象，含 `id`、`kbName`、`description`、`ownerId`、`visibility`、`courseId`、`docCount`、`status`、`createTime`。

#### 17.2.2 知识库列表（按权限过滤）  🔒

- **接口**：`GET /api/v1/kb`
- **Query**：`visibility`（可选）、`keyword`、`page`、`page_size`
- **说明**：只返回当前用户可访问的库——私有库仅本人所建，公共库全体可见，课程库限课程成员/任课教师。响应采用 2.4 分页结构。

#### 17.2.3 知识库详情  🔒

- **接口**：`GET /api/v1/kb/{id}`
- **说明**：无访问权返回 1004；不存在返回 1005。

#### 17.2.4 更新知识库  🔒

- **接口**：`PUT /api/v1/kb/{id}`
- **参数**：`kbName`、`description`、`status`；仅所有者或教务可操作，否则 1004。

#### 17.2.5 删除知识库  🔒

- **接口**：`DELETE /api/v1/kb/{id}`
- **说明**：逻辑删除；库内仍有文档时返回 409，需先清空文档。删除流程由应用级联清理 Qdrant 向量与文档记录。

### 17.3 文件存储（MinIO，Java 主服务签发）

#### 17.3.1 申请预签名上传 URL  🔒

- **接口**：`POST /api/v1/files/presign`
- **说明**：后端不中转文件流；拿到 URL 后前端直接 `PUT` 到 MinIO。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileName` | string | ✅ | 原始文件名 |
| `fileType` | string | ✅ | 扩展名，如 pdf / docx / txt |
| `bizType` | int | ✅ | 1 知识库文档，2 聊天媒体，3 头像，4 数字人资源，9 其他 |
| `fileSize` | long | 否 | 文件大小（字节） |

**响应 data**

```json
{
  "fileId": "5001",
  "bucketName": "documents",
  "objectKey": "kb/2026/11/uuid-filename.pdf",
  "uploadUrl": "https://minio.../presigned-put...",
  "expiresIn": 600
}
```

#### 17.3.2 上传完成确认  🔒

- **接口**：`POST /api/v1/files/{id}/confirm`
- **参数**：`fileHash`（可选，SHA-256）
- **说明**：后端校验 MinIO 对象已存在，返回完整文件记录；未确认的对象可由定时清理任务回收。

#### 17.3.3 申请预签名下载 URL  🔒

- **接口**：`GET /api/v1/files/{id}/download-url`
- **响应 data**：`{ "downloadUrl": "https://...", "expiresIn": 600 }`；不提供永久公开链接。

#### 17.3.4 文件列表  🔒

- **接口**：`GET /api/v1/files`
- **Query**：`bizType`、`keyword`、`page`、`page_size`；仅返回本人上传或有权限的记录。

### 17.4 知识库文档（Java 主服务）

#### 17.4.1 挂载文档到知识库  🔒

- **接口**：`POST /api/v1/kb/{kb_id}/documents`
- **前提**：文件已通过 17.3 完成上传，持有 `fileId`。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | string | ✅ | file_record.id |
| `docName` | string | ✅ | 文档名称 |
| `docType` | int | ✅ | 1 教材 2 课件 3 题库 4 真题 5 补充 |
| `chapterId` | string | 否 | 关联章节 |

**响应 data**：文档对象，`ingestStatus=0`（待处理），`kbId` 自动绑定。

#### 17.4.2 触发 RAG 入库  🔒

- **接口**：`POST /api/v1/kb/documents/{document_id}/ingest`
- **说明**：Java 校验权限后，在内部网络调 Python `17.6.4`；返回 `{ "taskId": "TASK...", "estimatedSeconds": 60 }`，进度沿用 4.9 查询。

#### 17.4.3 文档列表  🔒

- **接口**：`GET /api/v1/kb/{kb_id}/documents`
- **Query**：`ingestStatus`、`docType`、`keyword`、`page`、`page_size`。

#### 17.4.4 文档详情 / 删除  🔒

- 详情：`GET /api/v1/kb/documents/{id}`
- 删除：`DELETE /api/v1/kb/documents/{id}`，逻辑删除文档与切片记录，并通知 Python 清除对应 Qdrant 向量（按 document_id 的 payload 过滤）。

### 17.5 AI 流式对话（Python 服务，经 Nginx）

#### 17.5.1 流式对话  🔒 📌

- **接口**：`POST /api/v1/ai/chat/sessions/{session_id}/stream`
- **说明**：会话由 6.1 创建（v1.3 起 `POST /api/v1/chat/sessions` 增加 `kbId` 参数，其余不变）；Python 验 JWT 并查 Redis 黑名单，结束后自动回调 17.6.2 落库，前端无需单独调用落库接口。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | ✅ | 用户消息（语音时为 ASR 文本） |
| `msgType` | int | ✅ | 1 文本 2 语音 3 图片 4 文件 |
| `mediaUrl` | string | 否 | 媒体 URL |
| `clientMsgId` | string | ✅ | 客户端幂等 ID |

**SSE 事件流**（事件名沿用 6.2，字段对齐新数据模型）

```
event: start
data: {"messageId": 9101}

event: delta
data: {"delta": "我们先来看这道题..."}

event: citation
data: {"docId": 1101, "page": 23, "snippet": "...", "vectorId": "uuid"}

event: error
data: {"code": 3001, "msg": "AI 调用失败，已切换兜底话术"}

event: done
data: {"messageId": 9101, "promptTokens": 320, "completionTokens": 580,
       "totalTokens": 900, "modelName": "deepseek-v4-flash", "replyDurationMs": 4200}
```

错误约定：3001 调用失败触发兜底；3002 检索无结果退化为通用回答；1006 配额超限直接拒绝。

### 17.6 内部接口（`/internal`，仅 Docker 内部网络 + `X-Internal-Token`）

> 所有接口必须携带 `X-Internal-Token` 头，值由环境变量注入；外部经 Nginx 访问一律 404。

#### 17.6.1 拉取会话上下文（Python → Java）

- **接口**：`GET /internal/chat/sessions/{session_id}/context?userId={user_id}`
- **说明**：Java 校验会话归属，不匹配返回 403。
- **响应 data**：`{ "userId": 1001, "kbId": 7, "sceneType": 1, "messages": [ { "senderRole": 1, "msgType": 1, "content": "..." } ] }`

#### 17.6.2 消息落库（Python → Java）

- **接口**：`POST /internal/chat/messages`
- **说明**：一次调用写入用户消息与 AI 回复两条 `chat_message`、更新会话计数，并写一条 `ai_usage_log`。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sessionId` | string | ✅ | 会话 ID |
| `userMessage` | object | ✅ | `content`、`msgType`、`mediaUrl` |
| `assistantMessage` | object | ✅ | `content`、`citedSources`、`replyDurationMs` |
| `usage` | object | ✅ | `modelName`、`promptTokens`、`completionTokens`、`totalTokens`、`status`、`failReason` |

#### 17.6.3 知识库访问校验（Python → Java）

- **接口**：`GET /internal/kb/{kb_id}/access?userId={user_id}`
- **响应 data**：`{ "allowed": true, "visibility": 2 }`；`allowed=false` 时 Python 终止检索。

#### 17.6.4 触发文档入库（Java → Python）

- **接口**：`POST /internal/ingest`
- **参数**：`documentId`、`kbId`、`bucketName`、`objectKey`、`fileType`；Python 异步执行入库流水线。

#### 17.6.5 入库结果回调（Python → Java）

- **接口**：`PUT /internal/documents/{document_id}/ingest-callback`
- **参数**：`ingestStatus`（2 成功 / 3 失败）、`ingestError`、`chunks`（每项含 `chunkIndex`、`content`、`contentSummary`、`sourcePage`、`tokenCount`、`vectorId`）
- **说明**：Java 批量写 `knowledge_chunk`、更新文档状态与知识库 `docCount`。

### 17.7 跨服务认证契约

| 场景 | 凭证 | 约定 |
| --- | --- | --- |
| 外部用户请求 | `Authorization: Bearer {access_token}` | HS256；claims：`sub`（用户编号）、`userId`、`role`（1/2/3）、`tokenType`；Python 本地验签 |
| 注销一致性 | — | Python 读 Redis `ai_tutor:auth:blacklist:{token}`，命中即拒绝 |
| 服务间调用 | `X-Internal-Token` | 长随机串，环境变量注入，不入库 |

### 17.8 与 v1.1 接口的替代关系

| v1.1 接口 | v1.3 处理 |
| --- | --- |
| 4.7 文档上传（multipart 中转） | 由 17.3 预签名直传 + 17.4.1 挂载替代 |
| 4.8 触发解析 | 由 17.4.2 替代，底层经 Python 执行 |
| 4.9 任务进度查询 | 保留，继续复用 |
| 6.1 创建会话 | 保留，新增 `kbId` 参数 |
| 6.2 流式消息 | 由 17.5.1 替代，服务方改为 Python，SSE 事件名兼容 |
| 16.2 通用文件上传 | 由 17.3 预签名流程替代 |

---

### 文档结束

> 本文档为 v1.1 精简版。功能覆盖以 16.6「FR 功能覆盖对照表」为准，任何新增接口须先确认对应 FR 需求，避免冗余回归。
