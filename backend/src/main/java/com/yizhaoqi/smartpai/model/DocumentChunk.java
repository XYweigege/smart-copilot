package com.yizhaoqi.smartpai.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Blob;

/**
 * 文档切块实体类
 * 用于存储语义切块的原文和相关元数据（真正的向量存在 Elasticsearch）
 */
@Data
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(nullable = false, length = 32)
    private String fileMd5;

    @Column(nullable = false)
    private Integer chunkId;

    @Lob
    private String textContent;

    @Column(length = 32)
    private String modelVersion;
    
    /**
     * 上传用户ID
     */
    @Column(nullable = false, name = "user_id", length = 64)
    private String userId;
    
    /**
     * 文件所属组织标签
     */
    @Column(name = "org_tag", length = 50)
    private String orgTag;
    
    /**
     * 文件是否公开
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    /**
     * 父块ID（用于 Parent Chunk 上下文扩展）
     */
    @Column(name = "parent_chunk_id")
    private Integer parentChunkId;
}
