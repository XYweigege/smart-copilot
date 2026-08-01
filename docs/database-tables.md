# PaiSmart 数据库表说明（含示例）

> 数据库名：`paismart`（见 `docs/databases/ddl.sql`）。
> 下面按「表用途 + 关键字段 + 一条示例数据 + 在系统里怎么用」来介绍每张表，方便新人对照代码理解。

---

## 1. users —— 用户表

**用途**：存所有系统账号（普通用户 / 管理员）。登录、JWT 签发、组织隔离都依赖这张表。

**关键字段**
| 字段 | 说明 |
|------|------|
| `username` | 登录名，唯一 |
| `password` | BCrypt 加密后的密码 |
| `role` | `USER` / `ADMIN` |
| `org_tags` | 用户所属组织标签，逗号分隔，如 `default,hr,rd` |
| `primary_org` | 主组织，影响默认检索范围 |

**示例**
```sql
INSERT INTO users (username, password, role, org_tags, primary_org)
VALUES ('zhangsan',
        '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqnEOIFhoaUcH8zjz7gJzZ3x5x5x5x', -- 明文 zhangsan123
        'USER',
        'default,hr',
        'hr');
```
> 含义：用户 zhangsan，HR 部门普通用户，能检索 `hr` 和 `default` 组织及公开文档。

**在系统里怎么用**：`UserController.login` 用它校验密码；`org_tags` 用于所有检索的权限过滤（见后端文档 §0.1）。

---

## 2. organization_tags —— 组织标签表

**用途**：定义"组织 / 部门"的标签，支持父子层级（树形）。用于数据隔离的"边界"分配。

**关键字段**
| 字段 | 说明 |
|------|------|
| `tag_id` | 标签唯一标识（如 `hr`、`rd`） |
| `name` | 显示名 |
| `parent_tag` | 父标签（自引用外键，形成树） |
| `created_by` | 创建者（关联 users.id） |

**示例**（公司 → 部门两级）
```sql
INSERT INTO organization_tags (tag_id, name, description, parent_tag, created_by)
VALUES ('company',   '全公司',   '根组织',        NULL,    1),
       ('hr',        '人力资源', 'HR 部门',       'company', 1),
       ('rd',        '研发部',   '研发部门',      'company', 1);
```
> 含义：组织树为 全公司 → {人力资源, 研发部}。给用户分配 `hr` 标签，他就能看 HR 的数据。

**在系统里怎么用**：`AdminController` 的 org-tags 接口做增删改查；`OrgTagCacheService` 把树缓存到 Redis；用户表 `org_tags` 引用这些 tag。

---

## 3. file_upload —— 文件上传记录

**用途**：记录一次文件上传任务（分片上传 → 合并后写一条）。是"文档入库"的起点，后续解析、向量化都挂在这个 `file_md5` 上。

**关键字段**
| 字段 | 说明 |
|------|------|
| `file_md5` | 文件指纹，同一文件不重复存 |
| `file_name` | 原始文件名 |
| `total_size` | 字节数 |
| `status` | 0=上传中 / 1=已合并待解析 / 2=已解析完成 等 |
| `user_id` | 上传者 |
| `org_tag` | 归属组织（决定谁能检索这份文档） |
| `is_public` | 是否公开（公开则所有人可检索） |
| `merged_at` | 分片合并完成时间 |

**示例**
```sql
INSERT INTO file_upload (file_md5, file_name, total_size, status, user_id, org_tag, is_public)
VALUES ('3e25960a79dbc69b674cd4ec67a72c62', '公司制度手册.pdf', 2048000, 1, 'zhangsan', 'hr', 0);
```
> 含义：zhangsan 上传了《公司制度手册.pdf》，MD5 为 3e25…，归属 hr 组织、不公开，状态=已合并待解析。

**在系统里怎么用**：`UploadController` 合并分片后写此表并发送 Kafka 消息；`DocumentController` 删除文档时据此清理 ES/MinIO。唯一键 `(file_md5, user_id)` 防重复。

---

## 4. chunk_info —— 文件分块信息表

**用途**：记录文档被切成的每一块在存储系统（MinIO）里的位置。切片是"物理切"，为向量化/解析提供定位。

**关键字段**
| 字段 | 说明 |
|------|------|
| `file_md5` | 关联 file_upload 的文件 |
| `chunk_index` | 第几块（从 0 开始） |
| `chunk_md5` | 这一块的 MD5（去重/校验） |
| `storage_path` | 该块在 MinIO 的路径 |

**示例**（一份 3 块的文档）
```sql
INSERT INTO chunk_info (file_md5, chunk_index, chunk_md5, storage_path) VALUES
('3e25960a79dbc69b674cd4ec67a72c62', 0, 'a1b2c3...', 'uploads/chunks/3e25/part-0'),
('3e25960a79dbc69b674cd4ec67a72c62', 1, 'd4e5f6...', 'uploads/chunks/3e25/part-1'),
('3e25960a79dbc69b674cd4ec67a72c62', 2, 'g7h8i9...', 'uploads/chunks/3e25/part-2');
```
> 含义：《公司制度手册.pdf》被切成 3 块，分别存在 MinIO 的对应路径。

**在系统里怎么用**：`UploadService.mergeChunks` 记录分片；`ParseService` 按块从 MinIO 取出内容做 Tika 解析与切块（注意：此处是上传阶段的物理分片，与下文 `document_chunks` 的"语义切块"是不同维度）。

---

## 5. document_chunks —— 文档切块存储表（原名 document_vectors）

**用途**：这是**检索时的原文存根表**。文档解析后按语义切块（默认 512 字符/块），每块的**原文文本 + 权限/溯源元数据**存在这里；真正的向量存在 **Elasticsearch** 的 `dense_vector` 字段（本表不存向量）。ES 负责"检索定位"，本表负责"检索后取回原文、扩展上下文、抽图谱"。

**关键字段**
| 字段 | 说明 |
|------|------|
| `file_md5` | 来源文件 |
| `chunk_id` | 第几个语义块 |
| `text_content` | 该块纯文本（会被 ES 索引、也会被拼进 Prompt） |
| `model_version` | 向量模型版本（如 `text-embedding-v4`） |
| `user_id` | 上传者 |
| `org_tag` | 归属组织（检索权限过滤用） |
| `is_public` | 是否公开 |

**示例**
```sql
INSERT INTO document_chunks (file_md5, chunk_id, text_content, model_version, user_id, org_tag, is_public)
VALUES ('3e25960a79dbc69b674cd4ec67a72c62', 0,
        '年假申请需在钉钉提交，提前 3 个工作日审批。',
        'text-embedding-v4', 'zhangsan', 'hr', 0);
```
> 含义：手册第 0 块内容是"年假申请规则"。它的向量（2048 维）存在 ES 的 `dense_vector` 字段；`org_tag=hr` 保证只有 HR 组织用户能搜到。本表只存原文与元数据，不存向量。

**在系统里怎么用**：`ParseService` 调 `VectorizationService` 向量化后写入；`HybridSearchService` 召回时，用 `org_tag`/`is_public` 过滤，再把 `text_content` 作为上下文喂给 LLM（见后端文档 §2、§5）。

---

## 6. conversations —— 对话历史表

**用途**：存每一次"用户提问 + 系统回答"，按会话维度组织，支持历史回顾、多轮记忆、管理员审计。

**关键字段**
| 字段 | 说明 |
|------|------|
| `user_id` | 提问者 |
| `conversation_id` | 会话 UUID（与 Redis 中的会话 key 一致，按会话聚合多轮） |
| `question` | 用户问题 |
| `answer` | 系统回答 |
| `timestamp` | 发生时间 |

**示例**（同一会话的两轮）
```sql
INSERT INTO conversations (user_id, conversation_id, question, answer) VALUES
(12, 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
     '年假怎么申请？',
     '年假需在钉钉提交，提前 3 个工作日审批。（来源：公司制度手册.pdf）'),
(12, 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
     '要提前几天？',
     '需提前 3 个工作日。（来源：公司制度手册.pdf）');
```
> 含义：用户 12 在一个会话里连续问了两轮，都基于同一份 HR 文档。`conversation_id` 相同，便于"对话历史页"按会话回看。

**在系统里怎么用**：`ChatController` 落库每次问答；`ConversationService` 按 `user_id`/`conversation_id` 查询；`ConversationSummaryService` 对长会话生成摘要，避免上下文超长。

---

## 7. 表关系总览

```
users (1) ──< file_upload (n)        一个用户上传多个文件
users (1) ──< conversations (n)      一个用户有多条对话
users (1) ──< organization_tags (n)  一个用户可创建多个组织标签

file_upload (1) ──< chunk_info (n)       一个文件切成多个物理块
file_upload (1) ──< document_chunks (n) 一个文件生成多个语义切块

organization_tags (1) ──< organization_tags (n)  自引用，形成组织树
users.org_tags  ── 引用 ──> organization_tags.tag_id   （逗号分隔的多个 tag）
file_upload.org_tag / document_chunks.org_tag ── 引用 ──> organization_tags.tag_id
```

**贯穿全局的线索是 `file_md5`**：
`file_upload` → `chunk_info` → `document_chunks` 三张表都用它串联同一份文档；而 `org_tag` 是贯穿 `users` / `file_upload` / `document_chunks` 的**权限隔离键**。

---

> 文档版本：v1.0（数据库表说明） · 最后更新：2026-08-01
