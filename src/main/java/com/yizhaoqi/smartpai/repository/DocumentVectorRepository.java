package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocumentVectorRepository extends JpaRepository<DocumentVector, Long> {
    List<DocumentVector> findByFileMd5(String fileMd5); // 查询某文件的所有分块

    /**
     * 查询指定文件的某个父块下的所有子切片
     * 用于 Parent Chunk 上下文扩展：检索命中子切片后，回溯同一父块的其他子切片
     *
     * @param fileMd5 文件 MD5
     * @param parentChunkId 父块ID
     * @return 该父块下的所有子切片列表
     */
    List<DocumentVector> findByFileMd5AndParentChunkId(String fileMd5, Integer parentChunkId);
    
    /**
     * 删除指定文件MD5的所有文档向量记录
     * 
     * @param fileMd5 文件MD5
     */
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM document_vectors WHERE file_md5 = ?1", nativeQuery = true)
    void deleteByFileMd5(String fileMd5);
}
