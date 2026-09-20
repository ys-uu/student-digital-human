## 一、环境配置

### 后端 SpringBoot

1. 复制 `application-dev.example.yaml` 重命名为 `application-dev.yaml`，填写**本地数据库、AI 密钥等私有信息**；`application-dev.yaml` 加入`.gitignore`，**严禁提交至 Git 仓库**。
2. `application.yaml`、`application-dev.example.yaml` 作为公共模板，只保留通用配置，**不能写入任何密码、密钥、敏感凭证**，允许提交 Git。
3. 环境约定：本地开发使用 `dev` 环境；测试、上线分别使用 `test` / `prod` 环境。

### 前端 Vue3（admin-vue3）

1. 进入前端目录：`cd admin-vue3`，首次拉取代码执行 `npm install` 安装依赖；本地开发启动命令 `npm run dev`。
2. 后端接口、WebSocket 地址统一放在环境变量文件（`.env.development` / `.env.production`），**业务代码禁止硬编码后端 IP、端口**。
3. 提交前自检：依赖正常安装、项目成功启动，控制台无严重报错。

## 二、Git 分支 & Commit 提交规范

1. `main`：最终稳定上线分支，**禁止直接 push**；只能通过 PR 从 dev 分支合并，合并前完成代码简单评审。
2. `dev`：开发主分支，所有新功能都基于 dev 拉取分支；保证 dev 分支任何时刻都能正常启动运行。
3. 分支命名规则
    - 新增功能：`feature/模块名`，例：`feature/ws-chat`、`feature/ai-digital-human`
    - bug 修复：`fix/问题简述`，例：`fix/login-token-error`
4. 开发完成后，提交 Pull Request 合并到 dev；合并完成删除远程 + 本地 feature 分支。
5. commit 提交格式（必须遵守）
    - `feat: 新增xxx功能`：新增业务功能
    - `fix: 修复xxxbug`：缺陷修复
    - `refactor: xxx`：代码重构，不改变业务逻辑
    - `docs: 更新文档/注释`：README、数据库文档、注释修改
    - `style: 格式化代码`：仅缩进、空格、命名调整，无逻辑改动
    - `chore: 调整依赖/脚手架配置`
6. 提交前：拉取 dev 最新代码，提前解决代码冲突；不要一次性提交大量无关代码。

## 三、后端开发规范（匹配你的包结构）

>
> 固定包结构，**不允许随意新增、移动顶层包**
> common / config / controller / dto / entity / enums / interceptor / mapper / service / util / vo / ws

1. 数据库地址、密钥、第三方 API 凭证**从 yaml 配置读取，禁止在 Java 代码硬编码**。
2. 分层职责约束
    - `controller`：接收请求、参数校验，调用 service，返回统一 Result；**不写复杂业务逻辑**
- `service`：编写业务逻辑、事务控制，调用 mapper
- `mapper`：仅负责数据库 CRUD 操作
- `entity`：数据库表映射实体
- `dto`：接收前端传入的请求参数
- `vo`：封装返回给前端的数据
- `enums`：存放业务枚举，业务成员按需新增枚举类
- `interceptor`：拦截器（token 校验、ws 握手拦截）
- `ws`：WebSocket 相关代码
- `common`：全局异常、统一返回 Result、公共常量
- `util`：通用工具类
- `config`：配置类（WebConfig、WebSocketConfig、CorsConfig 等）
3. 接口遵循 REST 风格：GET 查询、POST 新增、PUT 更新、DELETE 删除；**所有接口统一返回 Result 包装对象**。
4. 数据库管理
   - 建表、表修改 SQL 脚本统一放在 `docs/sql`
   - 修改表结构前**必须同步全体组员**，同步更新 SQL 脚本；禁止私自直接修改测试库表。
5. 代码规范
   - 类名大驼峰，变量 / 方法小驼峰；枚举类统一后缀`Enum`
   - 复杂业务逻辑增加代码注释
   - 异常使用全局异常处理器，不要直接把原始异常堆栈返回前端
6. 新增 / 修改接口后，同步更新接口文档。

## 四、前端开发规范（匹配 admin-vue3 目录）

>
> src 目录固定结构：`api` / `assets` / `components` / `router` / `stores` / `utils` / `views`

1. 目录职责
   - `api`：所有后端请求接口封装（按模块拆分 js 文件）
   - `views`：页面业务组件（数字人聊天、预习、备考页面等）
   - `components`：公共复用组件（数字人形象、弹窗、输入框）
   - `router`：路由配置
   - `stores`：全局状态（用户登录信息、学习状态）
   - `utils`：工具函数、axios 请求封装、ws 工具
   - `assets`：静态资源，图片、css
2. 请求统一在 utils 封装 axios，统一做请求拦截、响应拦截、错误提示。
3. 页面业务代码和通用组件分离，通用组件抽入`components`。
4. 代码提交自检清单：项目正常启动、页面功能可用、代码格式化、无控制台报错。

## 五、团队协作约定

1. 开发新功能前，小组沟通需求，前后端提前约定接口入参、返回数据格式，避免重复开发。
2. 后端改动接口（入参、返回字段、URL）**必须第一时间通知前端组员**。
3. 遇到阻塞问题及时在群内同步，不要长时间独自卡壳。

## 六、红线（全员强制执行，禁止操作）

❌ 禁止提交本地私有配置、数据库密码、AI 密钥、token 等敏感信息到 Git
❌ 禁止直接 push 代码到 main 主分支
❌ 禁止代码内硬编码密钥、后端地址、魔法数字
❌ 未经组员沟通私自修改数据库表结构
❌ 不解决冲突强行合并代码，覆盖他人代码
❌ 提交代码不测试，导致 dev 分支无法启动