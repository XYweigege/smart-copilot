package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱管理控制器
 * 提供图谱统计、关系检索、图谱删除等管理接口
 */
@RestController
@RequestMapping("/api/v1/knowledge-graph")
public class KnowledgeGraphController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeGraphController.class);

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    /**
     * 获取知识图谱统计信息
     *
     * @return 统计信息，包括文档数、关键词数、关系数
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        logger.info("获取知识图谱统计信息");
        Map<String, Object> stats = knowledgeGraphService.getGraphStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 基于关系检索相关文档
     *
     * @param query 查询文本
     * @param userId 用户ID
     * @param orgTag 组织标签
     * @param topK 返回的最大文档数
     * @return 相关文档列表
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchByRelation(
            @RequestParam String query,
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "") String orgTag,
            @RequestParam(required = false, defaultValue = "10") int topK) {
        logger.info("知识图谱关系检索: query={}, userId={}, orgTag={}, topK={}", query, userId, orgTag, topK);
        List<Map<String, Object>> results = knowledgeGraphService.searchByRelation(query, userId, orgTag, topK);
        return ResponseEntity.ok(results);
    }

    /**
     * 删除指定文档的知识图谱
     *
     * @param fileMd5 文档 MD5
     * @return 操作结果
     */
    @DeleteMapping("/document/{fileMd5}")
    public ResponseEntity<Map<String, Object>> deleteDocumentGraph(@PathVariable String fileMd5) {
        logger.info("删除文档知识图谱: fileMd5={}", fileMd5);
        knowledgeGraphService.deleteDocumentGraph(fileMd5);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "文档图谱已删除");
        result.put("fileMd5", fileMd5);
        return ResponseEntity.ok(result);
    }

    /**
     * 手动为文档构建知识图谱
     *
     * @param fileMd5 文档 MD5
     * @param userId 用户ID
     * @param orgTag 组织标签
     * @return 操作结果
     */
    @PostMapping("/build")
    public ResponseEntity<Map<String, Object>> buildGraph(
            @RequestParam String fileMd5,
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "") String orgTag) {
        logger.info("手动构建文档知识图谱: fileMd5={}, userId={}, orgTag={}", fileMd5, userId, orgTag);
        knowledgeGraphService.buildGraphFromChunks(fileMd5, userId, orgTag);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "图谱构建完成");
        result.put("fileMd5", fileMd5);
        return ResponseEntity.ok(result);
    }
}
