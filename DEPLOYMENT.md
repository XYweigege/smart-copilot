# PaiSmart 部署与启动指南

## 📋 项目概述

PaiSmart 是一个基于 RAG（检索增强生成）的智能知识问答系统，包含：
- **后端**: Spring Boot 3.4.2 (Java 17) - 端口 8081
- **前端**: Vue 3 + TypeScript - 端口 5173
- **基础设施**: Docker (MySQL, Redis, Kafka, Elasticsearch, MinIO, Neo4j)

---

## 🛠️ 环境要求

### 必需软件
| 软件 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | 后端运行环境 |
| Maven | 3.9+ | 后端构建工具 |
| Node.js | 18+ | 前端运行环境 |
| pnpm | 8+ | 前端包管理器 |
| Docker Desktop | 最新版 | 容器化基础设施 |

### 硬件要求
- **内存**: 建议 8GB+（Elasticsearch 需要 2GB）
- **磁盘**: 至少 10GB 可用空间

---

## 🚀 快速启动（一键脚本）

### Windows PowerShell（管理员权限）

```powershell
# 1. 启动 Docker Desktop
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"

# 2. 等待 Docker 就绪（约 15 秒）
Start-Sleep -Seconds 15

# 3. 启动所有 Docker 服务
cd "项目路径/docs"
docker-compose up -d

# 4. 等待服务健康检查通过（约 60 秒）
Start-Sleep -Seconds 60

# 5. 启动后端（新终端窗口）
cd "项目路径/backend"
mvn spring-boot:run

# 6. 启动前端（新终端窗口）
cd "项目路径/frontend"
pnpm install && pnpm dev
```

---

## 📦 详细步骤

### 第一步：启动 Docker 基础设施

#### 1.1 启动 Docker Desktop
```powershell
# Windows
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
```

#### 1.2 检查 Docker 状态
```bash
docker info
```
确认输出显示 `Server Version` 信息。

#### 1.3 启动所有容器服务
```bash
cd docs
docker-compose up -d
```

**启动的服务：**
| 服务 | 端口 | 说明 | 默认密码 |
|------|------|------|----------|
| MySQL | 3306 | 数据库 | PaiSmart2025 |
| Redis | 6379 | 缓存/会话 | PaiSmart2025 |
| Kafka | 9092, 9093 | 消息队列 | - |
| Elasticsearch | 9200 | 全文搜索 | PaiSmart2025 |
| MinIO | 19000, 19001 | 对象存储 | admin/PaiSmart2025 |
| Neo4j | 7474, 7687 | 知识图谱 | neo4j/PaiSmart2025 |

#### 1.4 验证服务状态
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```
确保所有容器状态为 `Up` 或 `healthy`。

---

### 第二步：启动后端服务

#### 2.1 配置文件位置
主配置文件：`backend/src/main/resources/application.yml`

**关键配置项：**
```yaml
server:
  port: 8081  # 后端端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/paismart
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
    key: sk-your-api-key  # 替换为你的 API Key
```

#### 2.2 编译项目
```bash
cd backend
mvn clean compile
```

#### 2.3 启动后端
```bash
# 开发模式
cd backend
mvn spring-boot:run

# 或者后台运行（Windows）
cmd /c "start /b mvn spring-boot:run > backend.log 2>&1"
```

#### 2.4 验证后端启动成功
```bash
# 检查端口是否在监听
netstat -an | findstr ":8081"

# 测试登录接口（可选）
curl http://localhost:8081/api/v1/users/login -X POST -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

**预期输出：**
```json
{
  "code": 200,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

---

### 第三步：启动前端服务

#### 3.1 安装依赖
```bash
cd frontend
pnpm install
```

#### 3.2 环境配置
前端配置文件：`frontend/.env`

**关键配置：**
```env
# 后端 API 地址
VITE_SERVICE_BASE_URL=http://localhost:8081/api/v1

# 应用标题
VITE_APP_TITLE=PaiSmart

# 认证模式（static=静态路由，dynamic=动态路由）
VITE_AUTH_ROUTE_MODE=static

# 路由模式（hash/history/memory）
VITE_ROUTER_HISTORY_MODE=hash
```

#### 3.3 启动开发服务器
```bash
pnpm dev
```

#### 3.4 访问前端
打开浏览器访问：http://localhost:5173

---

### 第四步：登录系统

#### 默认账号
| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| testuser | test123 | 普通用户 |

#### 登录步骤
1. 打开浏览器访问 http://localhost:5173
2. 输入账号密码（或点击预设的"管理员"/"普通用户"按钮）
3. 点击"登录账号"按钮
4. 登录成功后自动跳转到聊天页面

---

## 🔧 常见问题排查

### 问题 1：Docker 启动失败
**症状：** `unable to connect to Docker daemon`

**解决方案：**
```powershell
# 确保 Docker Desktop 正在运行
Get-Process -Name "Docker Desktop"
# 如果没有运行，手动启动
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
```

### 问题 2：后端编译失败
**症状：** `COMPILATION ERROR: 找不到符号`

**常见原因及解决：**
1. **缺少依赖类**：检查 `backend/src/main/java/com/yizhaoqi/smartpai/service/` 目录下是否有被引用但未实现的类
2. **字段缺失**：检查实体类是否有必要的字段（如 `DocumentChunk.parentChunkId`）

**修复方法：**
```bash
cd backend
# 清理并重新编译
mvn clean compile

# 查看详细错误信息
mvn clean compile 2>&1 | findstr ERROR
```

### 问题 3：MySQL 连接失败
**症状：** `Could not create connection to database server`

**排查步骤：**
```bash
# 1. 检查 MySQL 容器状态
docker ps | findstr mysql

# 2. 检查 MySQL 日志
docker logs mysql --tail 50

# 3. 测试连接
docker exec mysql mysqladmin ping -h localhost -u root -pPaiSmart2025
```

### 问题 4：前端无法连接后端
**症状：** 控制台报错 `Network Error` 或 `ERR_CONNECTION_REFUSED`

**排查步骤：**
1. 确认后端已启动：浏览器访问 http://localhost:8081/api/v1/users/login
2. 检查 `.env` 文件中的 `VITE_SERVICE_BASE_URL` 是否正确
3. 确认防火墙未阻止 8081 端口

### 问题 5：Elasticsearch 启动缓慢
**症状：** ES 容器状态一直是 `health: starting`

**说明：** ES 首次启动需要安装 IK 分词器插件，可能需要 2-3 分钟

**查看日志：**
```bash
docker logs es --tail 100
```

### 问题 6：登录无反应
**症状：** 点击登录按钮没有任何反应

**排查步骤：**
1. 按 F12 打开浏览器开发者工具
2. 查看 Console 标签页是否有 JavaScript 错误
3. 查看 Network 标签页，点击登录后是否有请求发出
4. 如果有请求，检查响应状态码：
   - **401**: 用户名或密码错误
   - **500**: 后端异常，查看后端日志
   - **无请求**: 前端问题，检查控制台错误

---

## 📊 服务端口汇总

| 服务 | 端口 | 协议 | 用途 |
|------|------|------|------|
| 前端开发服务器 | 5173 | HTTP | Vue 应用 |
| 后端 Spring Boot | 8081 | HTTP | REST API / WebSocket |
| MySQL | 3306 | TCP | 数据库 |
| Redis | 6379 | TCP | 缓存 |
| Kafka Broker | 9092 | TCP | 消息队列 |
| Kafka Controller | 9093 | TCP | Kafka 控制器 |
| Elasticsearch | 9200 | HTTP | 搜索引擎 |
| MinIO API | 19000 | HTTP | 对象存储 API |
| MinIO Console | 19001 | HTTP | MinIO 管理界面 |
| Neo4j HTTP | 7474 | HTTP | Neo4j 管理界面 |
| Neo4j Bolt | 7687 | TCP | Neo4j 数据协议 |

---

## 🔐 默认凭据一览

| 服务 | 用户名 | 密码 | 用途 |
|------|--------|------|------|
| **系统登录** | admin | admin123 | 管理员账号 |
| **系统登录** | testuser | test123 | 普通用户 |
| MySQL | root | PaiSmart2025 | 数据库 |
| Redis | - | PaiSmart2025 | 缓存认证 |
| Elasticsearch | elastic | PaiSmart2025 | 搜索引擎 |
| MinIO | admin | PaiSmart2025 | 对象存储 |
| Neo4j | neo4j | PaiSmart2025 | 知识图谱 |

> ⚠️ **安全提示**：生产环境请务必修改所有默认密码！

---

## 🛑 停止服务

### 停止所有服务
```bash
# 1. 停止 Docker 服务
cd docs
docker-compose down

# 2. 停止后端（Ctrl+C 或关闭终端）

# 3. 停止前端（Ctrl+C 或关闭终端）
```

### 仅重启某个服务
```bash
# 重启 MySQL
docker restart mysql

# 重启后端（先停止再启动）
# 在后端终端按 Ctrl+C，然后重新执行 cd backend && mvn spring-boot:run
```

---

## 📝 开发注意事项

### 代码修改后热更新
- **前端修改**：保存后自动刷新（Vite HMR）
- **后端修改**：需要重新编译并重启
  ```bash
  # 使用 DevTools（如果 IDE 支持）可自动重启
  # 或手动执行：
  cd backend && mvn compile && # 然后 Ctrl+C 停止，再 mvn spring-boot:run
  ```

### 查看日志
```bash
# 后端日志（控制台直接输出）

# Docker 容器日志
docker logs -f mysql     # MySQL
docker logs -f redis     # Redis
docker logs -f es        # Elasticsearch
docker logs -f minio     # MinIO
docker logs -f neo4j     # Neo4j
```

### 数据库初始化
首次启动时，Spring Boot JPA 会自动创建数据库表结构（`ddl-auto: update`）。如需初始化管理员账号，可在数据库中执行：

```sql
-- 插入管理员账号（如果不存在）
INSERT INTO users (username, password, role, org_tags, primary_org, created_at, updated_at)
VALUES ('admin', '$2a$10$加密后的密码', 'ADMIN', 'default,admin', 'default', NOW(), NOW())
ON DUPLICATE KEY UPDATE username = username;
```

---

## 🌐 生产环境部署建议

### 1. 使用 Docker Compose 一键部署
创建 `docker-compose.prod.yml`，添加后端和前端的容器化配置。

### 2. 反向代理
使用 Nginx 统一入口：
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    location /api {
        proxy_pass http://backend:8081;
    }
    
    location /ws {
        proxy_pass http://backend:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
    
    location / {
        proxy_pass http://frontend:5173;
    }
}
```

### 3. 安全加固
- [ ] 修改所有默认密码
- [ ] 启用 HTTPS（SSL/TLS 证书）
- [ ] 配置防火墙规则
- [ ] 定期备份数据库
- [ ] 设置日志轮转

---

## 📞 技术支持

遇到问题时的排查顺序：

1. ✅ 检查 Docker 容器状态：`docker ps`
2. ✅ 检查后端日志：控制台输出或 `backend.log`
3. ✅ 检查前端浏览器控制台（F12）
4. ✅ 检查网络连通性：`telnet localhost 8081`
5. ✅ 检查端口占用：`netstat -ano | findstr :8081`

---

**文档版本**: v1.0  
**最后更新**: 2026-07-31  
**适用版本**: PaiSmart main 分支
