package com.yizhaoqi.smartpai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.entity.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * 阿里云百炼 Rerank 客户端
 * 调用 gte-rerank-v2 模型对检索结果进行精排
 */
@Service
public class RerankClient {

    private static final Logger logger = LoggerFactory.getLogger(RerankClient.class);
    private static final String RERANK_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数，初始化 WebClient 和配置
     *
     * @param apiKey 阿里云百炼 API Key
     * @param model  Rerank 模型名称
     */
    public RerankClient(@Value("${deepseek.api.key}") String apiKey,
                         @Value("${rerank.model:gte-rerank-v2}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(RERANK_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * 对搜索结果进行 Rerank 精排
     *
     * @param query     用户查询
     * @param candidates ES 粗排召回的候选结果
     * @param topK      最终返回数量
     * @return 精排后的搜索结果
     */
    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        // 候选数量 <= topK 时，无需 rerank
        if (candidates.size() <= topK) {
            logger.debug("候选数量({}) <= topK({})，跳过 rerank", candidates.size(), topK);
            return candidates;
        }

        logger.debug("开始 Rerank，查询: '{}', 候选数: {}, topK: {}", query, candidates.size(), topK);

        try {
            // 构建文档列表
            List<String> documents = new ArrayList<>(candidates.size());
            for (SearchResult sr : candidates) {
                // 截取前 512 字符，避免超长文本
                String text = sr.getTextContent();
                if (text.length() > 512) {
                    text = text.substring(0, 512);
                }
                documents.add(text);
            }

            // 构建请求体
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("documents", documents);
            requestBody.put("input", input);

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", Math.min(topK, candidates.size()));
            requestBody.put("parameters", parameters);

            // 调用 API
            String response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (response == null) {
                logger.warn("Rerank API 返回为空，返回原始排序");
                return candidates.subList(0, Math.min(topK, candidates.size()));
            }

            // 解析响应
            JsonNode root = objectMapper.readTree(response);
            JsonNode resultsNode = root.path("output").path("results");

            if (resultsNode.isMissingNode() || !resultsNode.isArray()) {
                logger.warn("Rerank 响应格式异常: {}", response);
                return candidates.subList(0, Math.min(topK, candidates.size()));
            }

            // 按 rerank 得分重新排序
            List<SearchResult> rerankedResults = new ArrayList<>();
            for (JsonNode resultNode : resultsNode) {
                int index = resultNode.path("index").asInt(-1);
                double relevanceScore = resultNode.path("relevance_score").asDouble(0.0);

                if (index >= 0 && index < candidates.size()) {
                    SearchResult original = candidates.get(index);
                    SearchResult reranked = new SearchResult(
                            original.getFileMd5(),
                            original.getChunkId(),
                            original.getTextContent(),
                            relevanceScore,
                            original.getUserId(),
                            original.getOrgTag(),
                            original.getIsPublic() != null ? original.getIsPublic() : false,
                            original.getFileName()
                    );
                    rerankedResults.add(reranked);
                    logger.debug("Rerank 结果: index={}, score={}, file={}, chunk={}",
                            index, relevanceScore, original.getFileMd5(), original.getChunkId());
                }
            }

            logger.info("Rerank 完成，输入 {} 条，输出 {} 条", candidates.size(), rerankedResults.size());
            return rerankedResults;

        } catch (Exception e) {
            logger.error("Rerank 调用失败，降级返回原始排序: {}", e.getMessage(), e);
            // 降级：直接截取 topK
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }
    }
}
