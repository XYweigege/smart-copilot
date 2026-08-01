# PaiSmart 新手入门指南

PaiSmart 是一个基于 RAG（检索增强生成）的智能知识问答系统。本指南面向**第一次接触本项目的同学**，按下面的顺序一步步来，你就能在本地把整套系统跑起来：

1. 准备环境（装软件）
2. **数据库初始化**（先把 MySQL 里的库和表建好）← 新手最容易漏的一步
3. 启动基础设施（Docker：MySQL / Redis / Kafka / ES / MinIO / Neo4j）
4. 启动后端（Spring Boot）
5. 启动前端（Vue 3）
6. 登录系统

> 如果你只是想快速跑起来，可以直接跳到「一键启动脚本」那一节。

---

## 一、环境准备（第一次需要）

| 软件 | 版本要求 | 用来干什么 |
|------|----------|------------|
| JDK | 17+ | 跑后端 |
| Maven | 3.9+ | 后端构建/启动 |
| Node.js | 18+ | 跑前端 |
| pnpm | 8+ | 前端包管理器（`npm i -g pnpm`） |
| Docker Desktop | 最新版 | 一键起 MySQL/Redis/Kafka/ES/MinIO/Neo4j |

硬件建议：**内存 8GB 以上**（Elasticsearch 一个就会吃掉 2GB），磁盘留 10GB 以上。

验证安装是否成功：

```bash
java -version      # 应显示 17.x
mvn -version       # 应显示 3.9.x
node -v            # 应显示 v18.x 或以上
pnpm -v            # 应显示 8.x 或以上
docker -v          # 应显示 Docker version ...
```

---

## 二、数据库初始化（重要！）

后端连接的数据库叫 **`paismart`**。项目里已经提供了建库建表的 SQL 脚本：`docs/databases/ddl.sql`。

请按下面步骤操作（以 Docker 后的 MySQL 为例，端口 `3306`，账号 `root`，密码 `PaiSmart2025`）：

### 2.1 先把数据库和表建好

方式 A：用 MySQL 客户端（如 Navicat / DBeaver）连上后，直接执行 `docs/databases/ddl.sql` 整个文件。

方式 B：用命令行（先确保 MySQL 已经在跑，见第三节的 Docker 启动）：

```bash
# 进入项目目录
cd <项目根目录>

# 把 ddl.sql 导入到 MySQL（会先 CREATE DATABASE paismart 再建表）
mysql -h 127.0.0.1 -P 3306 -u root -pPaiSmart2025 < docs/databases/ddl.sql
```

执行成功后会创建以下库和表：

- 数据库：`paismart`
- 表：`users`（用户）、`organization_tags`（组织标签）、`file_upload`（文件上传记录）、`chunk_info`（分块信息）、`document_chunks`（文档切块，原名 document_vectors）、`conversations`（对话历史）

### 2.2 初始化管理员账号（可选但推荐）

`ddl.sql` 只建了表结构，没有默认用户。如果你想直接用代码里的默认账号 `admin / admin123` 登录，需要手动插入一条管理员记录（密码是 BCrypt 加密后的字符串，下面给出的是与默认账号匹配的 hash）：

```sql
USE paismart;

INSERT INTO users (username, password, role, org_tags, primary_org, created_at, updated_at)
VALUES (
  'admin',
  '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqnEOIFhoaUcH8zjz7gJzZ3x5x5x5x',  -- 对应明文 admin123
  'ADMIN',
  'default,admin',
  'default',
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE username = username;
```

> 说明：如果后端开启了 JPA 自动建表（`ddl-auto: update`），启动时也会自动建表，但**手动执行 ddl.sql 更可靠**，能避免字段缺失导致的启动失败。建议新手两步都做。

---

## 三、启动基础设施（Docker）

所有中间件都放在 `docs/docker-compose.yaml` 里，一条命令全部启动。

### 3.1 启动 Docker Desktop

Windows：

```powershell
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
```

等待右下角 Docker 图标变绿（约 15 秒）。用 `docker ps` 能出结果就说明好了。

### 3.2 一键启动所有中间件

```bash
cd docs
docker-compose up -d
```

首次拉镜像会比较慢，等一会儿。启动的服务和端口如下：

| 服务 | 端口 | 默认账号 / 密码 |
|------|------|-----------------|
| MySQL | 3306 | root / PaiSmart2025 |
| Redis | 6379 | 密码：PaiSmart2025 |
| Kafka | 9092, 9093 | 无需账号 |
| Elasticsearch | 9200 | elastic / PaiSmart2025 |
| MinIO | 19000(API) / 19001(控制台) | admin / PaiSmart2025 |
| Neo4j | 7474(网页) / 7687(Bolt) | neo4j / PaiSmart2025 |

### 3.3 确认都起来了

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

看到状态是 `Up` / `healthy` 就行。Elasticsearch 第一次要装 IK 分词插件，可能要 2~3 分钟才变 healthy，属于正常。

---

## 四、启动后端（Spring Boot）

### 4.1 关键配置

后端主配置文件：`backend/src/main/resources/application.yml`。里面已经配好连上面那些中间件的地址，常用项如下（一般在你本地不用改）：

```yaml
server:
  port: 8081   # 后端端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/paismart   # 库名是 paismart
    username: root
    password: PaiSmart2025
  data:
    redis:
      host: localhost
      port: 6379
      password: PaiSmart2025
  kafka:
    bootstrap-servers: 127.0.0.1:9092

minio:
  endpoint: http://localhost:19000
  accessKey: admin
  secretKey: PaiSmart2025

elasticsearch:
  host: localhost
  port: 9200
  username: elastic
  password: PaiSmart2025

deepseek:
  api:
    url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen3.7-flash-2026-07-15
    key: sk-your-api-key   # 换成你自己的 Key，否则 AI 问答用不了
```

> ⚠️ 记得把 `deepseek.api.key` 换成你真实的 API Key，不然问答功能会报错（不影响系统启动）。

### 4.2 编译并启动

```bash
cd backend
mvn clean compile      # 先编译，确认没报错
mvn spring-boot:run    # 启动后端
```

看到日志里出现 `Started ... in xxx seconds` 就成功了。后端地址：`http://localhost:8081`

### 4.3 简单验证

```bash
curl http://localhost:8081/api/v1/users/login -X POST -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

返回带 `token` 的 JSON 就说明后端 + 数据库都通了。

---

## 五、启动前端（Vue 3）

打开**新的终端窗口**（后端那个别关）。

### 5.1 安装依赖

```bash
cd frontend
pnpm install
```

### 5.2 环境配置

前端配置在 `frontend/.env`，通常不用改：

```env
VITE_SERVICE_BASE_URL=http://localhost:8081/api/v1   # 指向上面的后端
VITE_APP_TITLE=PaiSmart
VITE_AUTH_ROUTE_MODE=static
VITE_ROUTER_HISTORY_MODE=hash
```

### 5.3 启动开发服务器

```bash
pnpm dev
```

启动后访问：**http://localhost:5173**

---

## 六、登录系统

默认账号（需要你按第二节 2.2 插入过管理员，或用你自己在库里建的账号）：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| testuser | test123 | 普通用户 |

打开 http://localhost:5173 → 输入账号密码 → 登录后进入聊天页面。

---

## 七、项目整体介绍

跑起来之后，下面从「它是谁」到「内部怎么跑」给你讲透。这一章偏技术向，看完基本能在脑子里把整条链路画出来。

### 7.1 它是什么：RAG 知识问答系统

PaiSmart 的本质是一个 **RAG（Retrieval-Augmented Generation，检索增强生成）系统**。

通用大模型的两个痛点：① 不懂你私有的内部资料；② 容易"幻觉"（编答案）。RAG 的解法是——**先把知识变成可检索的片段存起来，用户提问时先去检索最相关的片段，再把片段作为上下文喂给大模型，让它"带着证据"作答**。所以回答既基于你的私有资料，又能标注来源。

系统覆盖的完整生命周期：

```
文档入库（离线）                          用户提问（在线）
─────────────────                        ─────────────────
上传文件 → 解析文本 → 切块 → 向量化       提问 → 查询改写 → 召回
   → 写入 ES/MySQL/Neo4j                     → 精排 Rerank → 拼 Prompt → LLM 生成 → 流式返回
```

核心能力清单：

- **多格式文档解析**：Word / PDF / PPT / Excel / TXT，底层用 Apache Tika 提取文本
- **智能切块（Chunking）**：按配置 `chunk-size=512` 字符切分，控制上下文粒度
- **向量检索 + 关键词检索混合（Hybrid Search）**：基于 Elasticsearch，向量用 `dense_vector`，关键词用 IK 中文分词
- **Rerank 精排**：ES 召回 `candidate-size=30` 候选后，用 `gte-rerank-v2` 模型精排取 TopK
- **查询改写（Query Rewriting）**：多轮对话下用最近 `max-history=5` 轮历史把口语化问题改写成利于检索的查询
- **多轮对话记忆**：会话与消息存 MySQL，短期上下文缓存 Redis
- **知识图谱**：用 Neo4j 抽取文档实体与关系，支撑图谱化问答
- **多租户 / 权限**：按 `org_tags` 做组织级数据隔离；角色 `ADMIN` / `USER`；JWT 鉴权

### 7.2 技术架构总览

```
浏览器 / Vue 前端 (5173)
        │  HTTPS (axios, 带 JWT)
        ▼
Spring Boot 后端 (8081) ── com.yizhaoqi.smartpai
   controller  →  service  →  mapper(JPA/MyBatis)  →  entity
        │
        ├─ MySQL        业务数据：users / file_upload / chunk_info / document_chunks / conversations
        ├─ Redis        会话上下文缓存、限流
        ├─ Kafka        file-processing 主题（异步文档解析，带事务 + 死信队列 DLT）
        ├─ Elasticsearch 混合检索索引 + IK 中文分词
        ├─ MinIO        bucket=uploads，文件对象存储（兼容 S3 API）
        ├─ Neo4j        bolt://7687，知识图谱（实体/关系）
        └─ 外部 LLM API （WebClient 调用，OpenAI 兼容协议）
              ├─ 生成：dashscope 通义千问 qwen3.7-flash
              ├─ 向量：text-embedding-v4，维度 2048，batch-size=10
              └─ 精排：gte-rerank-v2
```

一句话：**前端负责交互与展示，后端用 Spring 分层编排业务，一组中间件负责"存 / 缓存 / 异步 / 检索 / 图谱"，外部 LLM 负责"把检索到的证据变成自然语言答案"。**

### 7.3 后端技术栈（精确到依赖与版本）

后端基于 **Spring Boot 3.4.2**（Java 17），Maven 构建，包名 `com.yizhaoqi.smartpai`。关键依赖：

| 能力 | 依赖 | 版本 / 说明 |
|------|------|------|
| Web 框架 | `spring-boot-starter-web` | Spring MVC，REST 接口 |
| 响应式 HTTP 客户端 | `spring-boot-starter-webflux` | 用 `WebClient` 调 LLM / Embedding / Rerank 接口，非阻塞 |
| ORM | `spring-boot-starter-data-jpa` | Hibernate，`ddl-auto: update` 自动建表；另有 MyBatis `mapper/` 做复杂查询 |
| 安全 | `spring-boot-starter-security` + `jjwt 0.11.5` | Spring Security 过滤器链 + JWT（HS256）鉴权 |
| 缓存 | `spring-boot-starter-data-redis` | 会话上下文、限流 |
| 消息 | `spring-kafka 3.2.1` | 生产者 `acks=all` + `enable-idempotence` + 事务；消费者组 `file-processing-group`；主题 `file-processing`，死信 `file-processing-dlt` |
| 检索 | `elasticsearch-java 8.10.0` | 官方 Java API Client，混合检索 + `dense_vector` |
| 对象存储 | `minio 8.5.12` | S3 兼容 SDK，bucket `uploads` |
| 图谱 | `spring-boot-starter-data-neo4j` | 实体/关系持久化 |
| 文档解析 | `tika-core` + `tika-parsers-standard-package 2.9.1` | 提取各格式文本 |
| 中文分词 | `hanlp portable-1.8.6` | 关键词抽取、图谱实体识别 |
| 工具 | `lombok 1.18.30`、`gson 2.10.1`、`commons-codec`、`commons-io` | Lombok 简化实体；MD5 校验等 |
| WebSocket | `spring-boot-starter-websocket` | 问答结果流式推送（双向，支持停止指令） |
| 校验 | `spring-boot-starter-validation` | 入参校验 |

### 7.4 前端技术栈（精确到依赖）

前端基于 **Soybean Admin** 脚手架（这是一套成熟的后台管理模板），Vue 3 + TypeScript + Vite 6.3.5，包管理 pnpm 8+。

| 类别 | 依赖 | 说明 |
|------|------|------|
| 框架 | `vue 3.5.13`、`vue-router 4.5.1`、`pinia 3.0.2` | 组合式 API、路由、状态管理 |
| UI 组件 | `naive-ui 2.41.0` | 主要组件库 |
| 样式 | `unocss 66.1.1`（含 preset-icons/preset-uno） | 原子化 CSS + 图标 |
| 工程化 | `@sa/scripts`、`@sa/axios`、`@sa/hooks`、`@sa/utils`、`@sa/color`、`@sa/materials` | Soybean 自研 monorepo 工具链（workspace:*） |
| 路由生成 | `@elegant-router/vue` | 约定式路由自动生成 |
| Markdown | `markdown-it` + `vue-markdown-shiki` + `@traptitech/markdown-it-katex` + `highlight.js` | 渲染答案/文档，支持代码高亮与数学公式 |
| 图表 | `echarts 5.6.0` | 知识图谱/统计可视化 |
| 实时通信 | `ws 8.x` | 与后端 WebSocket 对接，流式回答 |
| 国际化 | `vue-i18n 11.1.3` | 多语言 |
| 质量 | `eslint 9.26`、`vue-tsc 2.2.10`、`simple-git-hooks` | 提交前 `typecheck` + `lint` 卡口 |
| 其他 | `@vueuse/core`、`dayjs`、`nprogress`、`clipboard`、`vue-draggable-plus` | 工具函数、进度条、拖拽 |

前端开发命令（`package.json` 的 scripts）：

```bash
pnpm dev          # vite --mode test，本地开发
pnpm build        # vite build --mode prod，生产构建
pnpm typecheck    # vue-tsc 类型检查
pnpm lint         # eslint --fix
```

### 7.5 目录结构（真实）

```
PaiSmart-main/
├── backend/   (com.yizhaoqi.smartpai)
│   └── src/main/
│       ├── java/com/yizhaoqi/smartpai/
│       │   ├── controller/   # 接口层
│       │   │   ├── AuthController          # 登录/注册/登出（JWT 签发）
│       │   │   ├── ChatController          # 问答对话（遗留 WebSocket 实现，已弃用，见 Q6）
│       │   │   ├── DocumentController       # 知识库/文档管理
│       │   │   ├── UploadController         # 文件上传（写 MinIO + 发 Kafka）
│       │   │   ├── ConversationController   # 会话历史
│       │   │   ├── KnowledgeGraphController # 知识图谱查询
│       │   │   ├── SearchController         # 检索
│       │   │   ├── UserController           # 用户管理
│       │   │   └── AdminController          # 后台
│       │   ├── service/      # 业务逻辑（含 Embedding/Rerank/QueryRewrite 等子服务）
│       │   ├── mapper/       # MyBatis 映射
│       │   ├── entity/       # JPA 实体（对应表）
│       │   ├── config/       # Kafka/ES/MinIO/Redis/Security/WebClient 等配置
│       │   └── utils/        # 工具
│       └── resources/application.yml        # 主配置（见 7.7）
│
├── frontend/
│   └── src/
│       ├── views/        # 页面：chat / knowledge / login 等
│       ├── components/   # 可复用组件
│       ├── layouts/      # 布局框架
│       ├── router/       # 路由（elegant-router 生成）
│       ├── store/        # Pinia 状态
│       ├── service/      # 后端 API 封装（@sa/axios）
│       ├── hooks/        # 组合式函数
│       └── utils/        # 工具
│
├── docs/
│   ├── docker-compose.yaml   # 一键起 MySQL/Redis/Kafka/ES/MinIO/Neo4j
│   ├── databases/ddl.sql     # 建库建表 SQL（库名 paismart）
│   └── init_es.sh            # ES 索引 + IK 分词初始化
│
├── README.md / DEPLOYMENT.md
```

### 7.6 核心数据流（看懂这两条线就懂了系统）

**① 文档入库（离线，异步）**

```
UploadController 接收文件
  → 存入 MinIO (bucket=uploads)             ← 文件实体
  → 发送 Kafka 消息 (topic=file-processing) ← 事务性发送
        ↓ 消费者 (file-processing-group)
  → Apache Tika 解析文本
  → 按 chunk-size=512 切块
  → HanLP 抽取关键词/实体
  → Embedding API (text-embedding-v4, dim=2048) 向量化
  → 写入 Elasticsearch（chunk + 向量 + 关键词）
  → 写 MySQL（file_upload / chunk_info / document_chunks）
  → 可选：抽取关系写入 Neo4j（knowledge-graph.enabled=true）
  ⚠ 失败则进死信队列 file-processing-dlt，可重放
```

**② 用户问答（在线，流式）**

```
ChatController 收到问题 + conversationId
  → 查询改写（用最近 5 轮历史把问题写规范）         [query.rewrite.enabled]
  → 从 Redis/MySQL 取对话上下文
  → Elasticsearch 混合召回 candidate-size=30 候选
  → Rerank 精排 gte-rerank-v2 取 TopK              [rerank.enabled]
  → 按 ai.prompt.rules 拼 Prompt（结论先行 + 标注来源）
  → WebClient 调 LLM (temperature=0.3, max-tokens=2000)
  → 通过 WebSocket 流式返回前端（双向，支持停止生成）
  → 落库 conversations 表
```

生成参数（`application.yml` 的 `ai.generation`）：`temperature=0.3`、`top-p=0.9`、`max-tokens=2000`。系统提示词要求"先结论后论据、无信息答'暂无相关信息'"。

### 7.7 关键配置速查（application.yml）

| 配置项 | 值 | 含义 |
|--------|----|------|
| `server.port` | 8081 | 后端端口 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/paismart` | 业务库名，需与 `docs/databases/ddl.sql` 创建的库一致（已统一为 paismart） |
| `spring.jpa.hibernate.ddl-auto` | update | 启动时自动按实体更新表结构 |
| `spring.data.redis` | localhost:6379 / PaiSmart2025 | 缓存 |
| `spring.kafka.bootstrap-servers` | 127.0.0.1:9092 | 异步消息 |
| `minio.bucketName` | uploads | 文件桶 |
| `jwt.secret-key` | 内置 HS256 密钥 | JWT 签名 |
| `deepseek.api` | dashscope 兼容接口 / qwen3.7-flash | 生成模型 |
| `embedding.api` | text-embedding-v4 / dim=2048 / batch=10 | 向量化 |
| `elasticsearch` | localhost:9200 / elastic:PaiSmart2025 | 检索 |
| `spring.data.neo4j` | bolt://localhost:7687 | 图谱 |
| `knowledge-graph.enabled` | true | 是否抽取图谱 |
| `query.rewrite.enabled` | true / max-history=5 | 查询改写 |
| `rerank.enabled` | true / gte-rerank-v2 / candidate-size=30 | 精排 |
| `file.parsing.chunk-size` | 512 | 切块字符数 |

> ✓ 库名已统一为 `paismart`：`application.yml` 的 datasource 与 `docs/databases/ddl.sql` 创建的库名一致，执行 DDL 后可直接启动，无需额外处理。

---

## 一键启动脚本（给熟练之后的你）

```powershell
# 1. 启动 Docker 基础设施
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
Start-Sleep -Seconds 15
cd docs; docker-compose up -d
Start-Sleep -Seconds 60

# 2. 后端（另开一个终端）
cd backend; mvn spring-boot:run

# 3. 前端（再开一个终端）
cd frontend; pnpm install; pnpm dev
```

---

## 常见问题

**Q1：后端启动报 `Could not create connection to database server`**
→ MySQL 没起来或库没建。先 `docker ps` 看 mysql 是否 Up，再确认你执行过 `docs/databases/ddl.sql` 创建了 `paismart` 库。

**Q2：表不存在 / 字段缺失（编译或启动报找不到符号/未知列）**
→ 确认执行了 `docs/databases/ddl.sql`；或检查 `application.yml` 里 `ddl-auto` 是否为 `update`。

**Q3：Elasticsearch 一直是 starting**
→ 首次要装 IK 插件，等 2~3 分钟。看日志：`docker logs es --tail 100`。

**Q4：前端控制台 `Network Error`**
→ 后端没启动，或 `frontend/.env` 里 `VITE_SERVICE_BASE_URL` 写错。浏览器直接开 `http://localhost:8081/api/v1/users/login` 验证后端。

**Q5：AI 问答报错**
→ 多半是 `application.yml` 里 `deepseek.api.key` 没填真实 Key。

**Q6：聊天为什么用 WebSocket 而不是 SSE？**
→ 因为问答场景需要**双向通信**，而 SSE 只能服务器→客户端单向推送。具体三个原因：

1. **需要主动"停止"生成**：前端在 AI 回复过程中，发送按钮会变成「停止」按钮（`frontend/src/views/chat/modules/input-box.vue`），通过 `wsSend({type:'stop', _internal_cmd_token})` 向服务器回传停止指令，后端 `ChatWebSocketHandler` 收到后调用 `chatHandler.stopResponse` 中断流式输出。SSE 是只读通道，要实现停止必须额外再开一个 HTTP 接口，而 WebSocket 一条连接就搞定收发。
2. **同一通道收发问答**：用户提问（`processMessage`）和服务器流式推送（token 逐字返回）走的是同一个 `WebSocketSession`，连接天然支持双向。
3. **连接即可携带鉴权与会话参数**：路由 `/chat/{token}` 把 JWT 放在路径里（`ChatWebSocketHandler.extractUserId`），连接时还能带 `?conversationId=xxx` 实现「继续聊天」切换会话。

> 选择对照：如果只是一个纯「服务器推送、客户端不回传控制指令」的场景（通知、行情），SSE 更轻量。但本项目是带「中途打断」的流式对话，真正的全双工更合适。
>
> 备注：当前生效的是 `ChatWebSocketHandler`（`/chat/{token}`），见 `WebSocketConfig`。`ChatController` 里有一段遗留的 `extends TextWebSocketHandler` 旧实现（用 session id 作 userId、无鉴权），未被注册，建议后续清理避免混淆。

---

## 服务端口总览

| 服务 | 端口 | 用途 |
|------|------|------|
| 前端 | 5173 | Vue 开发服务器 |
| 后端 | 8081 | Spring Boot REST API |
| MySQL | 3306 | 数据库（库名 paismart） |
| Redis | 6379 | 缓存 |
| Kafka | 9092 / 9093 | 消息队列 |
| Elasticsearch | 9200 | 搜索 |
| MinIO | 19000 / 19001 | 对象存储 |
| Neo4j | 7474 / 7687 | 知识图谱 |

---

> 文档版本：v2.0（新手向） · 最后更新：2026-08-01
