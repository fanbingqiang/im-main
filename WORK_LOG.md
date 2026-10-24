# ti_live_app 工作日志（从 0 开始）

> 本日志按时间顺序记录本项目从接手的审查、修复到验证的全过程，供后续开发参考。

---

## 第 1 天：项目接手与全面审查

### 1.1 项目背景

**ti_live_app** 是一个基于微服务架构的直播/IM 平台，主要模块：

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| ti-live-gateway | 9000 | Spring Cloud Gateway 网关 + Token 鉴权 |
| ti-live-api | 8080 | BFF 聚合层（Dubbo Consumer，对外 REST） |
| ti-live-user-provider | 8081 | 用户、短信验证码、Token |
| ti-live-im-provider | 8085 | 聊天室 + WebSocket 实时消息 |
| ti-live-live-provider | 8086 | 直播流管理 |
| ti-live-vod-provider | 8087 | 点播视频管理 |
| id-generate-provider | 8090 | 分布式 ID 生成 |
| ti-live-web | 3000(dev) | Vue 3 前端 |

技术栈：Java 17 / Spring Boot 3.1.6 / Spring Cloud 2022.0.4 / Dubbo 3.2.11 / Nacos 2.2.4 / MyBatis-Plus / Redis / MySQL。

### 1.2 初始环境

- JDK 17（Temurin）、Maven 3.9.6 已安装，可用。
- git 未安装到 PATH（无法查看提交历史）。
- 本地无 node_modules（前端依赖未装）。

### 1.3 审查方法

1. 通读 `PROJECT_DOCUMENTATION.md`（逆向分析文档）核对代码现状。
2. 运行 `mvn compile` 验证全项目能否编译。
3. 逐个阅读核心代码文件，交叉验证文档描述与实现。

### 1.4 发现的问题清单

#### A. 构建阻断（最严重）

| # | 问题 | 影响 |
| --- | --- | --- |
| A1 | `cosid.version=1.20.5` 在 Maven 中央仓库**不存在**（1.20.x 最高 1.20.0） | 依赖无法下载 |
| A2 | 即使换版本，代码 import 的 `CosIdGenerator` / `StringCosIdGenerator` / `CosIdProvider` **在任何已发布版本中都不存在** | 编译必然失败 |

结论：id-generate 模块无法编译，必须移除 CosId、自实现 ID 生成。

#### B. 主流程跑不通（HIGH）

| # | 问题 | 后果 |
| --- | --- | --- |
| B1 | 验证码生成 6 位（100000~999999），登录校验却只接受 5 位 | 手机号登录必然失败 |
| B2 | WebSocket 连接建立时**从不调用** `enterRoom` | 在线人数恒 0、在线房间列表恒空 |
| B3 | 前端 WebSocket 硬编码 `userId='10001'`、token 兜底 `'test-token'`，后端却强制 token 与 userId 匹配 | 聊天连接必然被拒 |
| B4 | im/live/vod 的 Dubbo registry 带 `namespace: dev, group: DUBBO_GROUP`，api/gateway 不带 | 消费端找不到 provider |

#### C. 安全与配置问题（MEDIUM）

| # | 问题 |
| --- | --- |
| C1 | 登录 Cookie 未设置 HttpOnly |
| C2 | 验证码校验通过后不删除，可重放；发送无频控 |
| C3 | WebSocket 握手日志打印完整 query（含 token） |
| C4 | WebSocket 允许任意 Origin |
| C5 | 每次登录生成两个 token，一个被丢弃（Redis 孤儿键） |
| C6 | actuator 所有端点未认证暴露，且 `/actuator/**` 在白名单 |
| C7 | 网关 `/id/**` 路由指向无 HTTP 接口的模块（死路由） |
| C8 | API 层信任客户端传入的 `X-User-Id`，绕过网关可冒充任意用户 |
| C9 | live/vod 用 Spring Boot 2 的 `spring.redis.*`，Boot 3 下不生效 |
| C10 | bootstrap.yml 用 `127.0.0.1:8848`，application.yml 用 `nacos:8848`，不一致 |
| C11 | HLS 拉流地址硬编码 `localhost:8080`，与 API 端口冲突 |
| C12 | 同用户二次连接旧 WebSocket 会话不关闭（资源泄漏） |
| C13 | live/vod 后端无 HTTP 接口，前端页面全部 mock 数据 |

#### D. 次要问题（LOW）

- 死代码：`UserService`（无人引用）、im-provider 内重复的 `ImController`
- 类名拼写错误：`GatewayAppProperities`
- 消息 `senderName` 从不填充；前端心跳 `ping` 服务端无响应
- 房间 ID 用本地 `AtomicLong`（多实例冲突）；历史消息 SQL 直接拼接 LIMIT
- `getUserById` 的 `Long.parseLong` 未捕获异常
- VOD 缓存读写被注释未启用
- 前端 `router/index.js` 所有视图路径写错（`./views/` 相对 router 目录解析不到）

---

## 第 2 天：逐项修复

### 2.1 id-generate 模块重构（修复 A1、A2）

- 删除 `cosid-spring-boot-starter`、`cosid-jdbc`、mybatis、mysql、redis 依赖。
- 新增 [SnowflakeIdGenerator.java](id-generate/id-generate-provider/src/main/java/com/ti/id/provider/util/SnowflakeIdGenerator.java)：自实现雪花算法（41 位时间戳 + 5 位机房 + 5 位机器 + 12 位序列，含时钟回拨保护）。
- 重写 [IdGenerateProviderService.java](id-generate/id-generate-provider/src/main/java/com/ti/id/provider/impl/IdGenerateProviderService.java)，保留全部接口方法语义。
- 清理 application.yml 中 cosid/datasource 配置、删除无用的线程池配置与 cosid 建表脚本。
- **验证**：`mvn compile` 通过。

### 2.2 登录链路修复（修复 B1、C2、C5）

- [UserController.java](ti-live-api/src/main/java/com/it/live/controller/UserController.java)：验证码校验改为 6 位（与生成一致）；`getUserById` 增加异常捕获。
- [SmsService.java](ti-live-user/ti-live-user-provider/src/main/java/com/provider/service/SmsService.java)：发送加 **60 秒频控**；校验通过后**立即删除验证码**（防重放）。
- [UserMobileRPCService.java](ti-live-user/ti-live-user-provider/src/main/java/com/provider/impl/UserMobileRPCService.java)：移除内部生成的废弃 token（不再产生孤儿键）。

### 2.3 IM 在线人数修复（修复 B2、C12、D）

- [ChatWebSocketHandler.java](ti-live-im/ti-live-im-provider/src/main/java/com/ti/live/im/handler/ChatWebSocketHandler.java)：连接建立时补调 `enterRoom`；新增心跳 `pong` 响应；查询用户昵称填充 `senderName`。
- [ImRoomSessionManager.java](ti-live-im/ti-live-im-provider/src/main/java/com/ti/live/im/config/ImRoomSessionManager.java)：同用户重复连接先关闭旧会话。
- [ImRoomServiceImpl.java](ti-live-im/ti-live-im-provider/src/main/java/com/ti/live/im/Service/impl/ImRoomServiceImpl.java)：房间 ID 改用 Redis 原子自增（`im:room:seq`），解决多实例冲突。
- [ImMessageServiceImpl.java](ti-live-im/ti-live-im-provider/src/main/java/com/ti/live/im/Service/impl/ImMessageServiceImpl.java)：LIMIT 限制在 1~500。

### 2.4 前端 WebSocket 修复（修复 B3、D）

- [ChatRoom.vue](ti-live-web/src/views/ChatRoom.vue)：改用登录后存储的 `localStorage.userId / token`，去掉假 token 兜底；未登录跳转登录页；WS 端口可用 `VITE_WS_PORT` 覆盖。
- [Login.vue](ti-live-web/src/views/Login.vue)：登录成功后把 token / userId 写入 localStorage。
- [router/index.js](ti-live-web/src/router/index.js)：所有视图路径修正为 `../views/`；新增登录守卫。

### 2.5 Dubbo 注册一致性（修复 B4）

- im / live / vod 三个模块的 application.yml 移除 Dubbo registry 的 `namespace/group` 参数，与 api / gateway / user 保持一致。

### 2.6 安全加固（修复 C1、C3、C4、C6、C8）

- Cookie 设置 `HttpOnly`；登录响应体同时返回 token（供 WebSocket 使用）。
- WebSocket 握手日志不再打印完整 query。
- [WebSocketConfig.java](ti-live-im/ti-live-im-provider/src/main/java/com/ti/live/im/config/WebSocketConfig.java)：改为 `setAllowedOriginPatterns`，来源从 `ti.im.allowed-origins` 配置读取（默认 `http://localhost:*`）。
- 网关 actuator 只暴露 `health,info`，白名单仅保留 `/actuator/health`。
- 网关 [AuthorizationFilter.java](ti-live-gateway/src/main/java/com/ti/gateway/config/AuthorizationFilter.java)：
  - 清除客户端伪造的 `X-User-Id`，注入认证后的用户 ID；
  - 所有转发请求（含白名单）注入内部密钥 `X-Gateway-Secret`。
- 新增 [GatewaySecretFilter.java](ti-live-api/src/main/java/com/it/live/config/GatewaySecretFilter.java)：API 层校验网关密钥，**绕过网关直连返回 401**。

### 2.7 配置整理（修复 C7、C9、C10、C11、密码不一致）

- 网关移除 `/id/**` 死路由、移除不存在的 `/api/user/login` 白名单。
- live/vod 的 `spring.redis.*` 改为 `spring.data.redis.*`；数据库默认密码统一为 `root`。
- api 的 bootstrap.yml 与 application.yml 的 Nacos 地址统一为 `${NACOS_ADDR:nacos:8848}`。
- [LiveStreamServiceImpl.java](ti-live-live/ti-live-live-provider/src/main/java/com/ti/live/live/provider/impl/LiveStreamServiceImpl.java)：RTMP/HLS 地址改为 `live.media.rtmp-server / hls-server` 可配置，默认 HLS 端口改为 8090（不再与 API 冲突）。
- 用户模块补充 `spring.data.redis` 配置（此前缺失连默认 localhost）。

### 2.8 死代码与冗余清理

- 删除 `GatewayAppProperities.java`（拼写错误类）、`UserService.java`、im-provider 的重复 `ImController.java`。
- live/vod 实现类移除冗余的 `@Service` 注解（与 IM 模块一致，只保留 `@DubboService`）。

### 2.9 live/vod 打通 HTTP 通道（修复 C13）

- [ti-live-api/pom.xml](ti-live-api/pom.xml)：新增 live-interface、vod-interface 依赖。
- 新增 [LiveController.java](ti-live-api/src/main/java/com/it/live/controller/LiveController.java)（/api/live/list、create、start、end）与 [VodController.java](ti-live-api/src/main/java/com/it/live/controller/VodController.java)（/api/vod/list、search、info、upload）。
- [api/index.js](ti-live-web/src/api/index.js)：新增直播/点播接口封装。
- [Live.vue](ti-live-web/src/views/Live.vue)、[VodPage.vue](ti-live-web/src/views/VodPage.vue)：改为请求真实 API，**后端不可用时自动回退演示数据**。

### 2.10 VOD 缓存启用（修复已知问题 #9）

- [VodVideoServiceImpl.java](ti-live-vod/ti-live-vod-provider/src/main/java/com/ti/live/vod/provider/impl/VodVideoServiceImpl.java)：`getVideoInfo` 实现「缓存命中反序列化、未命中读库并写缓存（30 分钟 TTL）」；播放计数键加 1 天过期。

---

## 第 3 天：验证与结果

### 3.1 后端编译

```
mvn clean compile -DskipTests   →   BUILD SUCCESS（全部 10 个模块）
```

修复前：id-generate-provider 编译失败；修复后：全量通过。

### 3.2 前端构建

```
npm install（缓存放 D:\npm-cache） → 成功
npm run build                      → ✓ 99 modules transformed, built in 1.25s
```

### 3.3 修复前后对比

| 环节 | 修复前 | 修复后 |
| --- | --- | --- |
| 全项目编译 | ❌ id-generate 失败 | ✅ 全部通过 |
| 发送验证码 → 登录 | ❌ 6 位码被 5 位校验拒绝 | ✅ 位数一致、可重放消除、60s 频控 |
| 聊天室在线人数 | ❌ 恒为 0 | ✅ enterRoom 正确调用 |
| WebSocket 连接 | ❌ 假 token / 写死 userId 被拒 | ✅ 真实凭证、未登录跳转 |
| Dubbo 服务发现 | ❌ registry 参数不一致 | ✅ 全部统一 |
| 绕过网关直连 API | ❌ 可伪造 X-User-Id | ✅ 网关密钥校验 + 覆盖 X-User-Id |
| 直播/点播页面 | ❌ 纯 mock | ✅ 调真实 API，失败回退演示数据 |

---

## 第 4 天：本地部署与一键启动

### 4.1 本地环境准备

| 组件 | 版本 | 位置 |
| --- | --- | --- |
| JDK | Temurin 17.0.16 | `D:\tools\jdk17` |
| Maven | 3.9.6 | PATH |
| Nacos | 2.5.3（standalone） | `D:\tools\nacos`，端口 8848 |
| MySQL | 8.0 | 端口 3306 |
| Redis | 3.0.503（Windows） | `D:\tools\redis`，端口 6379 |

数据库：`ti_live_db`（用户/短信验证码）、`im_live`（聊天消息）、`ti_live`（直播/点播）。

### 4.2 启动过程中修复的问题

| # | 问题 | 解决 |
| --- | --- | --- |
| 1 | Nacos 配置中心默认连 9090 gRPC 端口失败 | 所有 Dubbo 地址（registry/config-center/metadata-report）统一加 `?serverAddr=${NACOS_ADDR}` 参数 |
| 2 | 配置里服务名 `nacos/redis` 在本地无法解析 | 全部参数化为 `${NACOS_ADDR:nacos:8848}`、`${REDIS_HOST:redis}` 等，本地通过环境变量注入 `localhost` |
| 3 | MySQL `Public Key Retrieval is not allowed` | JDBC URL 增加 `allowPublicKeyRetrieval=true` |
| 4 | 网关 `/api/**` 转发 404 | `UserController` 映射从 `/user` 改为 `/api/user`，与网关前缀对齐 |
| 5 | **JDK 17 + hessian2 反射限制**（`Unable to make field private int java.math.BigInteger.signum accessible`） | 所有服务启动加 JVM 参数 `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED` |
| 6 | 本机 7 个 Java 进程需独立窗口运行 | 新增 `start.bat` 一键启动（每个服务独立窗口） |

### 4.3 一键启动与部署

- [start.bat](start.bat)：Windows 本地一键启动 7 个微服务（自动注入 `NACOS_ADDR/REDIS_HOST/MYSQL_HOST/MYSQL_PASSWORD/GATEWAY_SECRET` 环境变量 + `--add-opens` 参数，每个服务独立窗口）。
- [Dockerfile.service](Dockerfile.service) / [Dockerfile.runtime](Dockerfile.runtime)：镜像内通过 `ENV` 注入基础设施地址，`ENTRYPOINT` 内置 `--add-opens` 参数。
- [docker-build.sh](docker-build.sh)：补齐 `id-generate-provider` 镜像构建与启动指南。

### 4.4 最终服务清单

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| id-generate-provider | 8090 | 分布式雪花 ID |
| ti-live-user-provider | 8081 | 用户/短信/token |
| ti-live-im-provider | 8085 | IM 聊天室 + WebSocket |
| ti-live-live-provider | 8086 | 直播流管理 |
| ti-live-vod-provider | 8087 | 点播管理 |
| ti-live-api | 8080 | BFF 聚合层 |
| ti-live-gateway | 9000 | 网关 + 鉴权 |
| ti-live-web | 80（Docker）/ 3000（dev） | Vue 3 前端 |

---

## 遗留事项与建议

1. **短信真实发送**：Cloopen SDK Bean 已配置但 `sendLoginCode` 仍未实际调用（需要真实短信账号），当前开发模式验证码只写库 + Redis。拿到账号后把 `CLOOPEN_ACCOUNT_SID` 等环境变量配上即可接入。
2. **多环境配置**：Nacos namespace 仍固定 `dev`，正式部署建议用环境变量区分。
3. **直播推流**：RTMP/HLS 已可配置，但需要外部媒体服务器（如 SRS/nginx-rtmp）才能真正推拉流。
4. **视频上传**：目前 `/api/vod/upload` 只登记 URL，未实现文件上传存储（后续可接对象存储）。
5. **订单/房间/礼物模块**：仍为占位，按业务需求再开发。
6. **Dashboard 统计**：页面仍为演示数据，可接入真实统计接口。
7. **部署密钥**：`GATEWAY_SECRET` 默认 `tlive-dev-secret`，生产环境务必通过环境变量覆盖。

---

*日志开始日期：2026-08-17 | 基于实际审查与修复过程记录*
