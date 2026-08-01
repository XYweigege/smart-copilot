package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.client.DeepSeekClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话摘要服务
 * 实现滑动窗口 + 消息摘要策略，减少多轮对话的 token 消耗
 * 
 * 策略：
 * - 保留最近 recentCount 条完整消息
 * - 更早的消息压缩为摘要
 * - 摘要 + 近期消息一起传给 LLM
 */
@Service
public class ConversationSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSummaryService.class);

    private final DeepSeekClient deepSeekClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${conversation.summary.enabled:true}")
    private boolean summaryEnabled;

    @Value("${conversation.summary.recent-count:6}")
    private int recentCount;

    @Value("${conversation.summary.trigger-threshold:10}")
    private int triggerThreshold;

    @Value("${conversation.summary.max-tokens:300}")
    private int maxSummaryTokens;

    private static final String SUMMARY_PREFIX = "conversation_summary:";

    public ConversationSummaryService(DeepSeekClient deepSeekClient,
                                       RedisTemplate<String, String> redisTemplate) {
        this.deepSeekClient = deepSeekClient;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取对话上下文：摘要 + 近期消息
     *
     * @param conversationId 会话ID
     * @param fullHistory    完整历史消息列表
     * @return 用于传给 LLM 的精简消息列表
     */
    public List<Map<String, String>> getContextWithSummary(String conversationId,
                                                            List<Map<String, String>> fullHistory) {
        if (!summaryEnabled || fullHistory.size() <= triggerThreshold) {
            logger.debug("消息数({}) <= 阈值({})，返回完整历史", fullHistory.size(), triggerThreshold);
            return fullHistory;
        }

        // 拆分：早期消息 + 近期消息
        int splitIndex = fullHistory.size() - recentCount;
        List<Map<String, String>> earlyMessages = fullHistory.subList(0, splitIndex);
        List<Map<String, String>> recentMessages = fullHistory.subList(splitIndex, fullHistory.size());

        // 获取或生成摘要
        String summary = getOrGenerateSummary(conversationId, earlyMessages);

        // 构建精简上下文：摘要作为 system 补充 + 近期消息
        List<Map<String, String>> context = new ArrayList<>();
        if (summary != null && !summary.isEmpty()) {
            Map<String, String> summaryMsg = new HashMap<>();
            summaryMsg.put("role", "system");
            summaryMsg.put("content", "【历史对话摘要】\n" + summary);
            context.add(summaryMsg);
        }
        context.addAll(recentMessages);

        logger.info("对话摘要生效: 总消息={}, 摘要覆盖={}, 保留近期={}, 摘要长度={}",
                fullHistory.size(), earlyMessages.size(), recentMessages.size(),
                summary != null ? summary.length() : 0);

        return context;
    }

    /**
     * 获取或生成摘要
     * 优先从 Redis 缓存读取，缓存未命中则调用 LLM 生成
     */
    private String getOrGenerateSummary(String conversationId, List<Map<String, String>> messages) {
        String summaryKey = SUMMARY_PREFIX + conversationId;

        // 尝试从缓存读取
        String cached = redisTemplate.opsForValue().get(summaryKey);
        if (cached != null && !cached.isEmpty()) {
            logger.debug("命中摘要缓存: {}", conversationId);
            return cached;
        }

        // 调用 LLM 生成摘要
        return generateSummary(conversationId, messages);
    }

    /**
     * 调用 LLM 生成对话摘要
     */
    private String generateSummary(String conversationId, List<Map<String, String>> messages) {
        try {
            // 构建对话文本
            StringBuilder dialogBuilder = new StringBuilder();
            for (Map<String, String> msg : messages) {
                String role = "user".equals(msg.get("role")) ? "用户" : "助手";
                String content = msg.get("content");
                // 截断过长的单条消息
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                dialogBuilder.append(role).append(": ").append(content).append("\n");
            }

            String systemPrompt = "你是一个对话摘要助手。请将以下对话内容压缩为简洁的摘要，保留关键信息（主题、结论、重要细节）。"
                    + "摘要应简洁明了，不超过" + maxSummaryTokens + "字。"
                    + "只输出摘要内容，不要输出其他内容。";

            String summary = deepSeekClient.syncCall(systemPrompt, dialogBuilder.toString());

            if (summary != null && !summary.isEmpty()) {
                // 缓存摘要，7天过期
                String summaryKey = SUMMARY_PREFIX + conversationId;
                redisTemplate.opsForValue().set(summaryKey, summary, Duration.ofDays(7));
                logger.info("生成对话摘要成功: conversationId={}, 摘要长度={}", conversationId, summary.length());
            }

            return summary;
        } catch (Exception e) {
            logger.error("生成对话摘要失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 清除摘要缓存（当用户开启新对话时调用）
     */
    public void clearSummary(String conversationId) {
        String summaryKey = SUMMARY_PREFIX + conversationId;
        redisTemplate.delete(summaryKey);
        logger.debug("清除对话摘要缓存: {}", conversationId);
    }
}
