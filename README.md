# PaiSmart - 企业级 AI 知识库管理系统

派聪明（PaiSmart）是一个基于 RAG（检索增强生成）技术的企业级 AI 知识库管理系统，提供智能文档处理、多路召回检索和 AI 问答能力。

## 核心特性

- 📄 **多格式文档处理**：支持 PDF、Word、Excel、PPT、Markdown 等格式
- 🔍 **多路召回检索**：ES 向量检索 + BM25 文本匹配 + Neo4j 知识图谱关系检索
- 🎯 **Rerank 精排**：基于 gte-rerank-v2 模型对粗排结果二次排序，显著提升准确率
- 🧠 **意图识别**：智能判断闲聊/知识问答/操作指令，避免无效检索
- ✍️ **查询改写**：结合对话历史改写模糊查询，解决指代消解问题
- 📊 **自适应检索**：根据查询复杂度动态调整检索数量
- 📦 **Parent Chunk 上下文**：子切片检索 + 父块上下文扩展，兼顾精度与完整性
- 💬 **对话摘要**：滑动窗口 + LLM 摘要策略，减少多轮对话 token 消耗
- 🕸️ **知识图谱**：基于 Neo4j 构建 Document-Keyword 关系图谱，支持关系推理检索
- 🤖 **AI 问答**：集成通义千问大模型，基于知识库内容生成精准回答
- 👥 **多租户架构**：通过组织标签实现数据隔离与权限管理
- ⚡ **实时对话**：基于 WebSocket 的流式响应，支持引用溯源

## 技术栈

### 后端

| 技术                  | 版本  | 说明           |
| --------------------- | ----- | -------------- |
| Java                  | 17    | 编程语言       |
| Spring Boot           | 3.4.2 | 应用框架       |
| Spring Security + JWT | -     | 安全认证       |
| MySQL                 | 8.0   | 关系型数据库   |
| Redis                 | 7.0+  | 缓存           |
| Elasticsearch         | 8.10+ | 搜索引擎       |
| Neo4j                 | 5.26+ | 知识图谱数据库 |
| Kafka                 | 3.2+  | 消息队列       |
| MinIO                 | -     | 对象存储       |
| Apache Tika           | -     | 文档解析       |
| WebFlux               | -     | 响应式编程     |

### 前端

| 技术       | 版本  | 说明       |
| ---------- | ----- | ---------- |
| Vue        | 3.5+  | UI 框架    |
| TypeScript | 5.8+  | 类型系统   |
| Vite       | 6.x   | 构建工具   |
| Naive UI   | 2.41+ | UI 组件库  |
| Pinia      | 3.x   | 状态管理   |
| UnoCSS     | -     | 原子化 CSS |
| pnpm       | 8.7+  | 包管理器   |

### 前端界面特性

界面采用克制、专业的企业后台风格（非 AI 炫光风格），便于少人工干预地统一维护：

- 🎨 **统一主题**：主色为企业蓝 `#2563eb`，中性深灰侧边栏与柔和阴影，主题默认跟随系统（`auto`），关闭页面切换动画。
- 💬 **聊天助手增强**：基于知识库的流式问答、引用溯源可下载原文；消息气泡底色区分用户/助手，来源链接统一主色；支持单条消息删除、重新生成、输入框一键清空与自动聚焦；顶部显示当前会话标题。
- 🔐 **登录页改版**：左侧品牌区（企业蓝渐变 + 产品简介）与右侧表单卡片双栏布局，小屏自动降级为简洁卡片；弱化 Logo 炫光，按钮统一 8px 圆角。
- 📐 **全局基础样式**：统一卡片/弹窗/输入/按钮的圆角与阴影，清晰字体层级，弱化菜单 hover 高光。

## 项目结构

### 后端

```
src/main/java/com/yizhaoqi/smartpai/
├── SmartPaiApplication.java      # 主应用程序入口
├── client/                        # 外部API客户端（DeepSeek、Embedding、Rerank）
├── config/                        # 配置类（Security、Neo4j、Redis等）
├── consumer/                      # Kafka消费者（文件处理、向量化）
├── controller/                    # REST API端点
├── entity/                        # 数据实体
├── exception/                     # 自定义异常
├── handler/                       # WebSocket处理器
├── model/                         # 领域模型
├── repository/                    # 数据访问层
├── service/                       # 业务逻辑（RAG、知识图谱、意图识别等）
└── utils/                         # 工具类
```

### 前端

```
frontend/
├── packages/           # 可复用模块
├── public/             # 静态资源
├── src/
│   ├── assets/         # SVG图标、图片
│   ├── components/     # Vue组件
│   ├── layouts/        # 页面布局
│   ├── router/         # 路由配置
│   ├── service/        # API集成
│   ├── store/          # 状态管理
│   └── views/          # 页面组件
└── package.json
```

## 快速开始

### 前置环境

- Java 17+
- Maven 3.8.6+
- Node.js 18.20+
- pnpm 8.7+
- Docker（用于启动基础设施服务）

### 1. 启动基础设施（Docker）

```bash
cd docs
docker compose up -d
```

启动的服务：

| 服务          | 端口        | 说明                     |
| ------------- | ----------- | ------------------------ |
| MySQL         | 3306        | 主数据库                 |
| Redis         | 6379        | 缓存                     |
| Kafka         | 9092        | 消息队列                 |
| MinIO         | 19000/19001 | 对象存储（API/控制台）   |
| Elasticsearch | 9200        | 向量检索                 |
| Neo4j         | 7474/7687   | 知识图谱（Browser/Bolt） |

### 2. 配置 API Key

编辑 `src/main/resources/application.yml`，配置 AI 服务的 API Key：

```yaml
# 聊天服务 API（阿里云百炼）
deepseek:
  api:
    url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen3.7-flash
    key: your-api-key

# 向量化服务 API
embedding:
  api:
    url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: text-embedding-v4
    key: your-api-key

# Rerank 精排
rerank:
  enabled: true
  model: gte-rerank-v2
```

### 3. 启动后端

```bash
mvn spring-boot:run
```

后端启动后访问：http://localhost:8081

### 4. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

前端启动后访问：http://localhost:9527

## 核心功能

### 知识库管理

- 支持多格式文档上传（PDF、Word、Excel、PPT、TXT、Markdown）
- 文件分片上传与断点续传
- 标签组织与分类管理
- 公开/私有文档权限控制
- 组织级别文档隔离

### RAG 检索增强流程

```
用户提问
  ↓
意图识别（闲聊/知识问答/操作指令）
  ↓
查询改写（结合对话历史，指代消解）
  ↓
自适应 topK（根据查询复杂度动态调整）
  ↓
多路召回：ES KNN + BM25 + Neo4j 图谱
  ↓
Rerank 精排（gte-rerank-v2）
  ↓
Parent Chunk 上下文扩展（子切片 → 父块）
  ↓
对话摘要压缩（滑动窗口 + LLM 摘要）
  ↓
LLM 流式生成（qwen3.7-flash）
```

### 知识图谱

基于 Neo4j 构建 Document-Keyword 关系图谱：

- 文件处理完成后自动提取关键词（HanLP）
- 构建 Document → Keyword 关系
- 检索时融合图谱结果，发现语义关联文档
- 支持手动构建/删除/查询图谱

### 企业级多租户

通过组织标签支持多租户架构：

- 每个用户可创建或加入多个组织
- 每个组织拥有独立的知识库和文档管理
- 支持精细的权限控制，确保数据安全

### 实时通信

基于 WebSocket 实现：

- 用户与 AI 的实时流式对话
- 响应式聊天界面
- 支持会话历史记录
- 引用溯源（标注答案来源文档）

## 部署说明

### Docker 部署

项目已提供完整的 `docker-compose.yaml` 配置文件，包含所有基础设施服务。

访问各服务控制台：

- MinIO Console: http://localhost:19001
- Neo4j Browser: http://localhost:7474
- Elasticsearch: http://localhost:9200

### 生产环境注意事项

1. 配置生产环境专用的 `application-prod.yml`
2. 修改数据库密码和 Redis 密码
3. 配置 HTTPS 证书
4. 配置 Nginx 反向代理
5. 设置日志级别为生产模式
6. 配置 API Key 环境变量，不要硬编码

## 技术架构

### 后端分层架构

```
┌─────────────────────────────────────────────────────────┐
│                    控制层 (Controller)                     │
│  - HTTP 请求处理  - 参数验证  - 响应格式化                  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                    服务层 (Service)                       │
│  - 业务逻辑处理  - 事务管理  - 跨数据源协调                  │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                    数据层 (Repository)                    │
│  - JPA 数据访问  - Elasticsearch 操作  - Neo4j 图谱操作    │
│  - MinIO 文件存储  - Redis 缓存                           │
└─────────────────────────────────────────────────────────┘
```

## 常见问题

### Q: 上传文件报错"没有bucket"？

A: MinIO 容器启动后需要手动创建 bucket。可通过 MinIO 控制台（http://localhost:19001）或执行 `mc mb myminio/uploads` 命令创建。

### Q: AI 聊天返回"服务器繁忙"？

A: 请检查 API Key 配置是否正确，账户是否有足够额度。可通过日志查看具体错误信息。

### Q: 如何修改默认端口？

A: 编辑 `application.yml` 中的 `server.port` 配置。

## License

MIT License
