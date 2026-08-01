# PaiSmart 后端逻辑技术文档（按页面分类）

> 本文以**前端页面**为维度组织，逐个说明每个页面对应的后端接口、调用链路与核心业务逻辑。
> 后端包：`com.yizhaoqi.smartpai`；统一前缀 `/api/v1`；鉴权方式：JWT（请求头 `Authorization: Bearer <token>`）。
> 阅读前建议先看 `README.md` 第七章的整体架构与数据流。

---

## 0. 公共机制（所有页面共用的后端逻辑）

### 0.1 鉴权与多租户隔离

- **登录链路**：`UserController.login` → `UserService.authenticate`（BCrypt 校验密码）→ `JwtUtils.generateToken(userId, orgTags)` 签发 JWT（HS256）。
- **拦截器**：`JwtAuthenticationFilter` 解析 token，把 `userId`、`orgTags` 放进 `RequestAttribute`，供后续 controller 使用（如 `SearchController` 用 `@RequestAttribute("userId")` 取）。
- **组织隔离**：几乎所有数据查询都带 `orgTag` 条件。用户属于多个 `org_tags`（逗号分隔），`primary_org` 为主组织。ADMIN 可跨组织；USER 只能看自己组织 + `isPublic=true` 的内容。
  - 实现见 `OrgTagCacheService`（组织树缓存到 Redis）与 `CustomUserDetailsService`（Spring Security 加载用户与权限）。

### 0.2 统一响应

- 普通接口：`{code, message, data}`（见 `SearchController` 的构造方式）。
- 问答接口：`ChatController` 用 **SSE/WebSocket 流式**返回（`text/event-stream`），而不是一次性 JSON。

### 0.3 异步与中间件

| 中间件 | 在本系统中的作用 |
|--------|------------------|
| Kafka | 文档解析异步化（`topic=file-processing`，含事务与死信队列 `file-processing-dlt`） |
| Elasticsearch | 混合检索（`dense_vector` 向量 + IK 关键词） |
| Redis | 会话上下文、组织树缓存、分片上传进度 |
| MinIO | 文件对象存储（bucket `uploads`） |
| Neo4j | 知识图谱（实体/关系） |
| 外部 LLM | 生成 / Embedding / Rerank（OpenAI 兼容协议，WebClient 调用） |

---

## 1. 登录页（`_builtin/login`）

**对应后端**：`UserController`（注册/登录/登出）、`CustomUserDetailsService`、`UserService`、`JwtUtils`

### 1.1 登录 `POST /api/v1/users/login`

```
请求体: { username, password }
→ UserService.authenticate(username, password)
    → 查 MySQL users 表（MyBatis/JPA）
    → BCryptPasswordEncoder.matches(raw, stored) 校验
    → 失败抛 BadCredentialsException
→ 成功：JwtUtils.generateToken(userId, orgTags) 生成 token
响应: { token, userId, username, orgTags, primaryOrg, role }
```

### 1.2 注册 `POST /api/v1/users/register`

```
{ username, password, role?, orgTags? }
→ 校验用户名唯一（查重）
→ BCryptPasswordEncoder.encode(password) 加密存储
→ 写 users 表
```

### 1.3 登录态维护

- `GET /api/v1/users/me`：返回当前用户资料（从 token 解析 userId 后查库）。
- `POST /api/v1/users/logout`、`/logout-all`：使 token 失效（基于 `TokenCacheService` 的黑名单/续期机制）。

> 注意：`AuthController` 也承担一部分鉴权/刷新相关接口，登录主逻辑在 `UserController`。

---

## 2. 聊天页（`chat`）

**对应后端**：`ChatController` → `ChatHandler` → `HybridSearchService` / `ElasticsearchService` / `VectorizationService` / Rerank / LLM

这是系统最核心的页面。整条链路见 `README.md` 7.6 ②，这里展开后端实现。

### 2.1 发起对话 `POST /api/v1/chat`（流式 SSE）

`ChatController` 接收 `{ conversationId, message, orgTag }`，交给 `ChatHandler.processMessage`：

```
ChatHandler.processMessage:
  1. 上下文加载
     - 若 conversationId 为空 → 新建会话（ConversationService.create）
     - 从 Redis/MySQL 取最近 max-history=5 轮历史消息
  2. 查询改写（query.rewrite.enabled=true）
     - 用历史 + 当前问题，调用 LLM 改写成利于检索的标准查询
  3. 混合召回
     - HybridSearchService.searchWithPermission(query, userId, orgTag, candidate-size=30)
       详见 §5 的检索逻辑
  4. Rerank 精排（rerank.enabled=true）
     - 调 gte-rerank-v2，对 30 个候选重排序，取 TopK
  5. 拼 Prompt
     - 按 ai.prompt.rules：先结论后论据、标注来源、无信息答"暂无相关信息"
     - 把 TopK 片段 + 历史 + 问题组装
  6. 调 LLM 生成（流式）
     - WebClient 调 deepseek.api（qwen3.7-flash）
     - 参数 ai.generation: temperature=0.3, top-p=0.9, max-tokens=2000
     - 通过 SSE 逐 token 推给前端
  7. 落库
     - 完整问答写 MySQL conversations 表
```

### 2.2 会话管理

- `GET /api/v1/chat/conversations`：列出当前用户会话
- `POST /api/v1/chat/switch-conversation`：切换会话
- `POST /api/v1/chat/delete-conversation`：删除会话

> 会话与消息持久化在 `ConversationService`；多轮记忆的短期上下文缓存在 Redis（`TokenCacheService`/`ConversationService` 内）。

---

## 3. 对话历史页（`chat-history`）

**对应后端**：`ConversationController` → `ConversationService` + `ConversationSummaryService`

### 3.1 接口

| 接口 | 作用 |
|------|------|
| `GET /api/v1/users/conversation` | 当前用户全部对话（支持 `?start_date=&end_date=` 时间过滤） |
| `GET /api/v1/chat/conversations` | 会话列表（同聊天页） |

### 3.2 后端逻辑

- `ConversationService`：从 MySQL `conversations` 表按 `userId` + `orgTag` 过滤查询；支持按日期范围检索。
- `ConversationSummaryService`：对长会话做**摘要生成**（调 LLM 把多轮对话压缩为摘要，存入 MySQL `conversations.summary`），用于历史回顾与上下文裁剪，避免上下文过长。

---

## 4. 知识库页（`knowledge-base`）

**对应后端**：`UploadController` / `ParseController` / `DocumentController` → `UploadService` / `ParseService` / `VectorizationService` / `Kafka`

这是「离线入库」链路，流程见 `README.md` 7.6 ①。

### 4.1 分片上传 `UploadController`

前端大文件走**分片上传 + 合并**：

```
POST /api/v1/upload/init         → UploadService.initUpload(fileMd5, fileName, totalChunks)
                                   在 MinIO 占位 / Redis 记录分片进度
POST /api/v1/upload/chunk        → 接收单个分片，写 MinIO（带 part 序号），更新 Redis 进度
POST /api/v1/upload/complete     → UploadService.mergeChunks(fileMd5)
                                   - 按序号合并 MinIO 分片为完整对象（bucket=uploads）
                                   - 计算/校验 fileMd5
                                   - 写 MySQL file_upload 表（状态=待解析）
                                   - 发送 Kafka 消息 topic=file-processing（事务性）
POST /api/v1/upload/status       → 查询上传进度（读 Redis）
```

> 关键设计：分片用 MinIO 的 multipart 能力，进度放 Redis，避免断点丢失。

### 4.2 异步解析（Kafka 消费者 → ParseService）

```
Kafka 消费者 (group=file-processing-group) 收到消息
→ ParseService.parseAndSave(fileMd5, inputStream)
   1. Apache Tika 解析二进制 → 纯文本
   2. 按 file.parsing.chunk-size=512 字符切块
   3. HanLP 抽取关键词/实体
   4. VectorizationService.vectorize()
      - 调 embedding API (text-embedding-v4, dim=2048, batch-size=10)
      - 得到每个 chunk 的向量
   5. 写入：
      - Elasticsearch：chunk + 向量(dense_vector) + 关键词（IK 分词）
      - MySQL：chunk_info / document_chunks
   6. 可选：knowledge-graph.enabled=true 时抽取关系 → Neo4j
→ 失败：消息进死信队列 file-processing-dlt，可重放
```

- 也提供手动解析入口 `POST /api/v1/parse`（`ParseController` → `ParseService.parseAndSave`）。

### 4.3 文档管理 `DocumentController`

| 接口 | 作用 |
|------|------|
| `GET /api/v1/documents` | 列出知识库文档（按 orgTag 过滤） |
| `DELETE /api/v1/documents/{id}` | 删除文档（同步删 ES 索引 + MinIO 对象 + MySQL 记录） |
| `GET /api/v1/documents/{id}/chunks` | 查看某文档的分块内容 |

`DocumentService` 负责 MySQL/ES/MinIO 的联动删除，保证一致性。

---

## 5. 检索 / 搜索（知识库内搜索框）

**对应后端**：`SearchController` → `HybridSearchService` + `ElasticsearchService`

### 5.1 混合检索 `GET /api/v1/search/hybrid?query=&topK=`

检索后端 = `HybridSearchService`（`searchWithPermission`），核心思路是 **kNN 向量召回 + 关键词 must 过滤 + 权限 filter + BM25 rescore 重排 + 知识图谱融合**。一次 ES 查询里完成四件事：

```
HybridSearchService.searchWithPermission(query, userId, orgTag, topK):
  0. 查用户有效组织标签 getUserEffectiveOrgTags（决定能看哪些文档）
  1. 问题向量化：embedToVectorList(query) → queryVector（调 EmbeddingClient）
  2. 一次 ES bool 查询内做四件事：
     ① KNN 向量召回：knn field=vector queryVector=k cosine（recallK=topK*30 候选窗口）
     ② 关键词 must：match(textContent=query) 走 IK 倒排索引（BM25），强制字面命中
     ③ 权限 filter：自己(userId) OR 公开(isPublic) OR 所属组织(orgTag，含层级) 三层 should
     ④ BM25 rescore：窗口内用关键词重排，queryWeight=0.2 / rescoreWeight=1.0（以关键词为主、语义为辅）
  3. s.size(topK) 截取最终结果
  4. fuseGraphResults：Neo4j 按实体关系找相关文档，ES 已有者 +图谱分*0.3 提升，无者作为补充，统一排序取 topK
  5. 返回 SearchResult：{ fileMd5, chunkId, textContent, score, userId, orgTag, isPublic }
```

ES 索引 `knowledge_base` 三类索引并存（见 `es-mappings/knowledge_base.json`）：

| 字段 | 索引类型 | 用在这步 |
|------|----------|----------|
| `textContent`（IK 分词） | 倒排索引 | ② 关键词 must、④ BM25 rescore |
| `fileMd5` / `orgTag`（keyword） | 倒排索引 | ③ 权限过滤 |
| `vector`（`dense_vector` + `index:true` cosine） | 向量索引(HNSW) | ① KNN 召回 |

**兜底机制**（保证向量服务挂了仍能检索）：
- 向量生成失败 → 退化为 `textOnlySearchWithPermission`（纯 BM25 关键词 + 权限 filter + `minScore(0.3)` 阈值）。
- 整个混合检索异常 → 再退化到纯文本搜索。
- 注意：KNN 的 `k` 由配置 `elasticsearch.knn.k` 控制，默认覆盖在全量候选上。

- 未带 userId 时走 `search()`（仅公开内容）。
- `ElasticsearchService` 封装 ES Java Client 8.10 的查询构造（bool query + knn）。
- **写入幂等**：`ElasticsearchService.bulkIndex` 用 `fileMd5_chunkId` 作为 ES 文档 `_id`，同一块重复写入会覆盖而非新增（避免重复文档）。

---

## 6. 知识图谱（知识库页内嵌 / 独立模块）

**对应后端**：`KnowledgeGraphController` → `KnowledgeGraphService`（Neo4j）

### 6.1 接口

| 接口 | 作用 |
|------|------|
| `GET /api/v1/knowledge-graph/stats` | 图谱统计（文档数、实体数、关系数） |
| `GET /api/v1/knowledge-graph/search` | 基于关系检索相关文档（图谱问答） |
| `POST /api/v1/knowledge-graph/build` | 手动为某文档构建图谱（fileMd5） |
| `DELETE /api/v1/knowledge-graph/document/{fileMd5}` | 删除某文档的图谱 |

### 6.2 后端逻辑

- `KnowledgeGraphService.buildGraphFromChunks`：从 MySQL `chunk_info` 取文本，用 HanLP 抽取实体与关系，写入 Neo4j（bolt://7687，`spring.data.neo4j`）。
- `searchByRelation`：把查询映射到图谱路径查询，召回相关文档片段。
- `getGraphStats`：统计节点/关系数量，供前端可视化（ECharts）。

---

## 7. 组织标签管理页（`org-tag`）

**对应后端**：`AdminController` 的 org-tags 系列 + `OrgTagCacheService`

### 7.1 接口（`/api/v1/admin`）

| 接口 | 作用 |
|------|------|
| `GET /api/v1/admin/org-tags` | 列出组织标签 |
| `GET /api/v1/admin/org-tags/tree` | 组织标签**树形结构**（父子层级） |
| `POST /api/v1/admin/org-tags` | 新建标签 |
| `PUT /api/v1/admin/org-tags/{tagId}` | 修改标签 |
| `DELETE /api/v1/admin/org-tags/{tagId}` | 删除标签 |
| `PUT /api/v1/admin/users/{userId}/org-tags` | 给用户分配组织标签 |

### 7.2 后端逻辑

- 组织标签存储于 MySQL `organization_tags` 表，支持层级（parent_id）。
- `OrgTagCacheService`：把组织树缓存到 Redis，避免每次查库；变更时失效缓存。
- 数据隔离的"开关"就在这里——用户被分配到哪些 `org_tags`，决定他能搜到哪些文档。

---

## 8. 用户管理页（`user`）

**对应后端**：`AdminController` 的 users 系列 + `UserController`

### 8.1 接口（`/api/v1/admin`）

| 接口 | 作用 |
|------|------|
| `GET /api/v1/admin/users` / `/users/list` | 用户列表（分页，按 orgTag 过滤） |
| `POST /api/v1/admin/users/create-admin` | 创建管理员账号 |
| `POST /api/v1/admin/knowledge/add` | 后台添加知识（文档） |
| `DELETE /api/v1/admin/knowledge/{documentId}` | 删除知识 |
| `GET /api/v1/admin/system/status` | 系统状态（中间件连通性、统计） |
| `GET /api/v1/admin/user-activities` | 用户活跃度统计 |
| `GET /api/v1/admin/conversation` | 后台查看全部对话 |
| `POST /api/v1/admin/migrate-minio` | 迁移 MinIO 文件（运维） |
| `POST /api/v1/admin/clear-all-data` | 清空全部数据（高危！） |

### 8.2 后端逻辑

- `UserService` 负责用户 CRUD、密码加密、组织分配。
- 后台对话查看（`/admin/conversation`）绕过用户隔离，供管理员审计。
- `system/status` 探测 MySQL/Redis/ES/Kafka/MinIO/Neo4j 健康状态。

---

## 9. 个人中心页（`personal-center`）

**对应后端**：`UserController` 的个人信息接口

| 接口 | 作用 |
|------|------|
| `GET /api/v1/users/me` | 当前用户资料 |
| `PUT /api/v1/users/primary-org` | 切换主组织（影响默认检索范围） |
| `GET /api/v1/users/upload-orgs` | 当前用户可上传到的组织列表 |
| `POST /api/v1/users/logout` / `/logout-all` | 登出 |

> 核心点：`primary_org` 决定新建文档/检索的默认组织上下文；`upload-orgs` 是从 `org_tags` 解析出的可上传目标。

---

## 附录 A：页面 → 后端接口速查表

| 前端页面 | 主要后端 Controller | 核心 Service | 涉及中间件 |
|----------|---------------------|--------------|------------|
| 登录 `_builtin/login` | UserController | UserService / CustomUserDetailsService / JwtUtils | MySQL、Redis( token) |
| 聊天 `chat` | ChatController | ChatHandler / HybridSearchService / VectorizationService | ES、LLM、Redis、MySQL |
| 对话历史 `chat-history` | ConversationController | ConversationService / ConversationSummaryService | MySQL |
| 知识库 `knowledge-base` | UploadController / ParseController / DocumentController | UploadService / ParseService / VectorizationService / DocumentService | MinIO、Kafka、ES、MySQL、Neo4j |
| 检索/搜索 | SearchController | HybridSearchService / ElasticsearchService | ES |
| 知识图谱 | KnowledgeGraphController | KnowledgeGraphService | Neo4j |
| 组织标签 `org-tag` | AdminController | OrgTagCacheService | MySQL、Redis |
| 用户管理 `user` | AdminController / UserController | UserService | MySQL |
| 个人中心 `personal-center` | UserController | UserService | MySQL、Redis |

## 附录 B：一句话数据流回顾

- **入库**：Upload(分片→MinIO) → Kafka → Tika解析 → 切块 → Embedding → ES/MySQL → (Neo4j)
- **问答**：改写 → ES混合召回 → Rerank → 拼Prompt → LLM 流式 → 落库
- **隔离**：所有查询带 `orgTag`，ADMIN 跨组织，USER 仅本组织 + 公开

---

> 文档版本：v1.0（按页面分类的后端逻辑） · 最后更新：2026-08-01
