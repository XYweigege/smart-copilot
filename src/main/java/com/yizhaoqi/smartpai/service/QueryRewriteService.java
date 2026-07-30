package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.client.DeepSeekClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 查询改写服务
 * 利用 LLM 结合对话历史，将模糊查询改写为具体、可检索的查询
 */
@Service
public class QueryRewriteService {

    private static final Logger logger = LoggerFactory.getLogger(QueryRewriteService.class);

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Value("${query.rewrite.enabled:true}")
    private boolean enabled;

    @Value("${query.rewrite.max-history:5}")
    private int maxHistory;

    /**
     * 改写用户查询
     * 结合对话历史，将指代不清、模糊的查询改写为具体查询
     *
     * @param userMessage 用户原始查询
     * @param history 对话历史（最近 N 轮）
     * @return 改写后的查询，如果改写失败或禁用则返回原始查询
     */
    public String rewrite(String userMessage, List<Map<String, String>> history) {
        if (!enabled) {
            logger.debug("查询改写已禁用，返回原始查询");
            return userMessage;
        }

        // 如果查询已经足够具体（包含多个关键词），直接返回
        if (isSpecificQuery(userMessage)) {
            logger.debug("查询已足够具体，跳过改写: {}", userMessage);
            return userMessage;
        }

        try {
            logger.info("开始改写查询: {}", userMessage);

            // 构建改写提示词
            String systemPrompt = buildRewritePrompt();
            String userPrompt = buildUserPrompt(userMessage, history);

            // 调用 LLM 进行改写
            String rewritten = deepSeekClient.syncCall(systemPrompt, userPrompt);

            // 清理结果（去除可能的引号、换行等）
            rewritten = cleanRewrittenQuery(rewritten);

            if (rewritten.isEmpty() || rewritten.equals(userMessage)) {
                logger.debug("改写结果与原始查询相同，使用原始查询");
                return userMessage;
            }

            logger.info("查询改写成功: '{}' -> '{}'", userMessage, rewritten);
            return rewritten;
        } catch (Exception e) {
            logger.error("查询改写失败: {}", e.getMessage(), e);
            return userMessage; // 失败时返回原始查询
        }
    }

    /**
     * 判断查询是否已经足够具体
     * 具体查询特征：包含多个关键词、不含代词、长度适中
     */
    private boolean isSpecificQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String trimmed = query.trim();

        // 长度过短（< 4 字符）通常不够具体
        if (trimmed.length() < 4) {
            return false;
        }

        // 包含代词（这、那、它、他、她）通常需要改写
        String[] pronouns = {"这", "那", "它", "他", "她", "这个", "那个", "哪些", "如何", "怎么"};
        for (String pronoun : pronouns) {
            if (trimmed.contains(pronoun)) {
                return false;
            }
        }

        // 包含多个中文字符或英文单词，认为足够具体
        long chineseChars = trimmed.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long englishWords = trimmed.split("\\s+").length;

        return chineseChars >= 6 || englishWords >= 3;
    }

    /**
     * 构建系统提示词
     */
    private String buildRewritePrompt() {
        return """
                你是一个查询改写助手。你的任务是根据对话历史，将用户的模糊查询改写为具体、可检索的查询。

                改写规则：
                1. 如果查询包含代词（这、那、它等），根据上下文替换为具体名词
                2. 如果查询过于简短，补充相关上下文信息
                3. 保持查询的核心意图不变
                4. 只输出改写后的查询，不要输出任何解释或额外内容
                5. 如果查询已经足够具体，直接返回原查询

                示例：
                历史：用户问"Kafka 如何配置？"，AI 回答了配置方法
                当前查询："那个端口呢？"
                改写结果："Kafka 端口配置"

                历史：用户问"ES 性能优化"，AI 回答了优化建议
                当前查询："还有呢？"
                改写结果："Elasticsearch 性能优化方法"
                """;
    }

    /**
     * 构建用户提示词（包含对话历史）
     */
    private String buildUserPrompt(String userMessage, List<Map<String, String>> history) {
        StringBuilder prompt = new StringBuilder();

        // 添加对话历史（最多 maxHistory 轮）
        if (history != null && !history.isEmpty()) {
            prompt.append("对话历史：\n");
            int startIdx = Math.max(0, history.size() - maxHistory * 2);
            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> msg = history.get(i);
                String role = msg.get("role");
                String content = msg.get("content");

                // 截断过长的内容
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }

                if ("user".equals(role)) {
                    prompt.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    prompt.append("AI: ").append(content).append("\n");
                }
            }
            prompt.append("\n");
        }

        // 添加当前查询
        prompt.append("当前查询: ").append(userMessage).append("\n");
        prompt.append("改写结果: ");

        return prompt.toString();
    }

    /**
     * 清理改写结果
     * 去除可能的引号、换行、多余空格等
     */
    private String cleanRewrittenQuery(String query) {
        if (query == null) {
            return "";
        }

        String cleaned = query.trim();

        // 去除首尾引号
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // 去除换行
        cleaned = cleaned.replaceAll("\\r?\\n", " ");

        // 合并多余空格
        cleaned = cleaned.replaceAll("\\s+", " ");

        return cleaned;
    }
}
