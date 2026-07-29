# PaiSmart - 企业级 AI 知识库管理系统

派聪明（PaiSmart）是一个基于 RAG（检索增强生成）技术的企业级 AI 知识库管理系统，提供智能文档处理和检索能力。

## 核心特性

- 📄 **多格式文档处理**：支持 PDF、Word、Excel、PPT、Markdown 等格式
- 🔍 **智能检索**：基于 Elasticsearch 的混合检索（关键词 + 向量）
- 🤖 **AI 问答**：集成大模型，基于知识库内容生成精准回答
- 👥 **多租户架构**：通过组织标签实现数据隔离与权限管理
- ⚡ **实时对话**：基于 WebSocket 的流式响应

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.4.2 | 应用框架 |
| Spring Security + JWT | - | 安全认证 |
| MySQL | 8.0 | 关系型数据库 |
| Redis | 7.0+ | 缓存 |
| Elasticsearch | 8.10+ | 搜索引擎 |
| Kafka | 3.2+ | 消息队列 |
| MinIO | - | 对象存储 |
| Apache Tika | - | 文档解析 |
| WebFlux | - | 响应式编程 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5+ | UI 框架 |
| TypeScript | 5.8+ | 类型系统 |
| Vite | 6.x | 构建工具 |
| Naive UI | 2.41+ | UI 组件库 |
| Pinia | 3.x | 状态管理 |
| UnoCSS | - | 原子化 CSS |
| pnpm | 8.7+ | 包管理器 |

## 项目结构

### 后端

```
src/main/java/com/yizhaoqi/smartpai/
├── SmartPaiApplication.java      # 主应用程序入口
├── client/                        # 外部API客户端
├── config/                        # 配置类
├── consumer/                      # Kafka消费者
├── controller/                    # REST API端点
├── entity/                        # 数据实体
├── exception/                     # 自定义异常
├── handler/                       # WebSocket处理器
├── model/                         # 领域模型
├── repository/                    # 数据访问层
├── service/                       # 业务逻辑
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
- Docker (可选，用于启动基础设施服务)

### 1. 启动基础设施（Docker）

```bash
cd docs
docker compose -f docker-compose.yaml up -d
```

### 2. 配置 API Key

编辑 `src/main/resources/application.yml`，配置 AI 服务的 API Key：

```yaml
# 聊天服务 API
deepseek:
  api:
    url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen-plus
    key: your-api-key

# 向量化服务 API
embedding:
  api:
    url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: text-embedding-v4
    key: your-api-key
```

### 3. 启动后端

```bash
cd 项目根目录
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

### AI 驱动的 RAG 实现

1. **文档解析**：使用 Apache Tika 解析上传的文档内容
2. **智能分块**：将文档语义切分为合适的文本块
3. **向量化**：调用 Embedding 模型为每个文本块生成高维向量
4. **混合检索**：结合向量相似度与关键词匹配，检索相关文档
5. **AI 生成**：将检索结果作为上下文，交给 LLM 生成回答

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

## 部署说明

### Docker 部署

项目已提供完整的 `docker-compose.yaml` 配置文件，包含 MySQL、Redis、Kafka、MinIO、Elasticsearch 等基础设施。

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
│  - JPA 数据访问  - Elasticsearch 操作  - MinIO 文件存储    │
└─────────────────────────────────────────────────────────┘
```

### RAG 流程图

```
用户提问 → 问题向量化 → 混合检索(ES) → 重排序 → 构建Prompt → LLM生成 → 流式响应
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
