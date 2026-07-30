package com.yizhaoqi.smartpai.service;

import com.hankcs.hanlp.HanLP;
import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱服务
 * 负责从文档中提取关键词并构建知识图谱，支持基于关系的文档检索
 */
@Service
public class KnowledgeGraphService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeGraphService.class);

    private final Driver neo4jDriver;

    @Autowired
    private DocumentVectorRepository documentVectorRepository;

    @Value("${knowledge-graph.enabled:true}")
    private boolean enabled;

    @Value("${knowledge-graph.keyword-top-n:20}")
    private int keywordTopN;

    @Value("${knowledge-graph.min-keyword-length:2}")
    private int minKeywordLength;

    @Value("${knowledge-graph.max-relationship-per-doc:50}")
    private int maxRelationshipPerDoc;

    public KnowledgeGraphService(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    /**
     * 为文档构建知识图谱
     * 提取文档关键词，创建 Document 和 Keyword 节点，建立 CONTAINS_KEYWORD 关系
     *
     * @param fileMd5 文件 MD5
     * @param textContent 文档文本内容
     * @param userId 用户 ID
     * @param orgTag 组织标签
     */
    public void buildGraph(String fileMd5, String textContent, String userId, String orgTag) {
        if (!enabled) {
            logger.debug("知识图谱功能已禁用，跳过构建");
            return;
        }

        try {
            logger.info("开始为文档构建知识图谱，fileMd5: {}", fileMd5);

            // 1. 提取关键词
            List<String> keywords = extractKeywords(textContent);
            if (keywords.isEmpty()) {
                logger.warn("未提取到关键词，跳过图谱构建，fileMd5: {}", fileMd5);
                return;
            }

            logger.info("提取到 {} 个关键词: {}", keywords.size(), keywords);

            // 2. 在 Neo4j 中创建节点和关系
            createDocumentAndKeywordsNodes(fileMd5, keywords, userId, orgTag);

            logger.info("知识图谱构建完成，fileMd5: {}", fileMd5);
        } catch (Exception e) {
            logger.error("构建知识图谱失败，fileMd5: {}", fileMd5, e);
        }
    }

    /**
     * 基于关系检索相关文档
     * 通过查询文本提取关键词，在知识图谱中查找包含这些关键词的文档
     *
     * @param queryText 查询文本
     * @param userId 用户 ID（用于权限过滤）
     * @param orgTag 组织标签（用于权限过滤）
     * @param topK 返回的最大文档数
     * @return 相关文档的 fileMd5 列表及其关系得分
     */
    public List<Map<String, Object>> searchByRelation(String queryText, String userId, String orgTag, int topK) {
        if (!enabled) {
            logger.debug("知识图谱功能已禁用，返回空结果");
            return new ArrayList<>();
        }

        try {
            logger.info("开始基于关系检索，query: {}, userId: {}", queryText, userId);

            // 1. 从查询文本中提取关键词
            List<String> queryKeywords = extractKeywords(queryText);
            if (queryKeywords.isEmpty()) {
                logger.warn("查询文本未提取到关键词");
                return new ArrayList<>();
            }

            logger.debug("查询关键词: {}", queryKeywords);

            // 2. 在知识图谱中查找相关文档
            List<Map<String, Object>> results = findRelatedDocuments(queryKeywords, userId, orgTag, topK);

            logger.info("关系检索完成，找到 {} 个相关文档", results.size());
            return results;
        } catch (Exception e) {
            logger.error("关系检索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 使用 HanLP 提取文本关键词
     *
     * @param text 输入文本
     * @return 关键词列表
     */
    private List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 使用 HanLP 提取关键词，返回 topN 个
            List<String> keywords = HanLP.extractKeyword(text, keywordTopN);

            // 过滤掉长度过短的关键词
            return keywords.stream()
                    .filter(kw -> kw != null && kw.length() >= minKeywordLength)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("关键词提取失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 在 Neo4j 中创建 Document 和 Keyword 节点及关系
     *
     * @param fileMd5 文件 MD5
     * @param keywords 关键词列表
     * @param userId 用户 ID
     * @param orgTag 组织标签
     */
    private void createDocumentAndKeywordNodes(String fileMd5, List<String> keywords, String userId, String orgTag) {
        String cypher = """
            MERGE (d:Document {fileMd5: $fileMd5})
            SET d.userId = $userId, d.orgTag = $orgTag, d.createdAt = timestamp()
            WITH d
            UNWIND $keywords as keyword
            MERGE (k:Keyword {word: keyword})
            MERGE (d)-[:CONTAINS_KEYWORD]->(k)
            """;

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, Values.parameters(
                        "fileMd5", fileMd5,
                        "userId", userId,
                        "orgTag", orgTag,
                        "keywords", keywords
                ));
                return null;
            });

            logger.debug("成功创建文档节点和 {} 个关键词关系，fileMd5: {}", keywords.size(), fileMd5);
        } catch (Exception e) {
            logger.error("创建 Neo4j 节点失败，fileMd5: {}", fileMd5, e);
            throw e;
        }
    }

    /**
     * 在知识图谱中查找与查询关键词相关的文档
     *
     * @param queryKeywords 查询关键词列表
     * @param userId 用户 ID
     * @param orgTag 组织标签
     * @param topK 返回的最大文档数
     * @return 相关文档列表，每个文档包含 fileMd5 和 score
     */
    private List<Map<String, Object>> findRelatedDocuments(List<String> queryKeywords, String userId, String orgTag, int topK) {
        // Cypher 查询：查找包含任一查询关键词的文档，按匹配关键词数量排序
        String cypher = """
            MATCH (d:Document)-[:CONTAINS_KEYWORD]->(k:Keyword)
            WHERE k.word IN $queryKeywords
              AND (d.userId = $userId OR d.orgTag = $orgTag)
            WITH d, COUNT(k) as matchedKeywords, COLLECT(k.word) as keywords
            RETURN d.fileMd5 as fileMd5, matchedKeywords, keywords
            ORDER BY matchedKeywords DESC
            LIMIT $topK
            """;

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, Values.parameters(
                    "queryKeywords", queryKeywords,
                    "userId", userId,
                    "orgTag", orgTag,
                    "topK", topK
            ));

            List<Map<String, Object>> documents = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> doc = new HashMap<>();
                doc.put("fileMd5", record.get("fileMd5").asString());
                doc.put("matchedKeywords", record.get("matchedKeywords").asInt());
                doc.put("keywords", record.get("keywords").asList(v -> v.asString()));
                // 计算关系得分：匹配的关键词数量 / 查询关键词总数
                double score = (double) record.get("matchedKeywords").asInt() / queryKeywords.size();
                doc.put("score", score);
                documents.add(doc);
            }

            return documents;
        } catch (Exception e) {
            logger.error("查询知识图谱失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 从数据库读取分块内容并构建知识图谱
     *
     * @param fileMd5 文件 MD5
     * @param userId 用户 ID
     * @param orgTag 组织标签
     */
    public void buildGraphFromChunks(String fileMd5, String userId, String orgTag) {
        if (!enabled) {
            logger.debug("知识图谱功能已禁用，跳过构建");
            return;
        }

        try {
            // 从数据库读取该文件的所有分块内容
            List<DocumentVector> vectors = documentVectorRepository.findByFileMd5(fileMd5);
            if (vectors == null || vectors.isEmpty()) {
                logger.warn("未找到分块内容，跳过图谱构建，fileMd5: {}", fileMd5);
                return;
            }

            // 合并所有分块文本
            String fullText = vectors.stream()
                    .map(DocumentVector::getTextContent)
                    .collect(Collectors.joining("\n"));

            logger.info("从 {} 个分块合并文本，总长度: {}，fileMd5: {}", vectors.size(), fullText.length(), fileMd5);

            // 构建知识图谱
            buildGraph(fileMd5, fullText, userId, orgTag);
        } catch (Exception e) {
            logger.error("从分块构建知识图谱失败，fileMd5: {}", fileMd5, e);
        }
    }

    /**
     * 删除文档及其相关关系
     *
     * @param fileMd5 文件 MD5
     */
    public void deleteDocumentGraph(String fileMd5) {
        if (!enabled) {
            return;
        }

        String cypher = """
            MATCH (d:Document {fileMd5: $fileMd5})
            DETACH DELETE d
            """;

        try (Session session = neo4jDriver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, Values.parameters("fileMd5", fileMd5));
                return null;
            });
            logger.info("已删除文档图谱，fileMd5: {}", fileMd5);
        } catch (Exception e) {
            logger.error("删除文档图谱失败，fileMd5: {}", fileMd5, e);
        }
    }

    /**
     * 获取知识图谱统计信息
     *
     * @return 统计信息 Map
     */
    public Map<String, Object> getGraphStats() {
        Map<String, Object> stats = new HashMap<>();

        if (!enabled) {
            stats.put("enabled", false);
            return stats;
        }

        stats.put("enabled", true);

        try (Session session = neo4jDriver.session()) {
            // 统计文档节点数
            Result docResult = session.run("MATCH (d:Document) RETURN COUNT(d) as count");
            if (docResult.hasNext()) {
                stats.put("documentCount", docResult.next().get("count").asInt());
            }

            // 统计关键词节点数
            Result kwResult = session.run("MATCH (k:Keyword) RETURN COUNT(k) as count");
            if (kwResult.hasNext()) {
                stats.put("keywordCount", kwResult.next().get("count").asInt());
            }

            // 统计关系数
            Result relResult = session.run("MATCH ()-[r:CONTAINS_KEYWORD]->() RETURN COUNT(r) as count");
            if (relResult.hasNext()) {
                stats.put("relationshipCount", relResult.next().get("count").asInt());
            }
        } catch (Exception e) {
            logger.error("获取图谱统计信息失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }
}
