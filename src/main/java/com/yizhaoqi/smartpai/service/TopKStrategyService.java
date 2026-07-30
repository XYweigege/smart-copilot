package com.yizhaoqi.smartpai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 自适应 TopK 策略服务
 * 根据查询类型和复杂度动态调整检索数量
 */
@Service
public class TopKStrategyService {

    private static final Logger logger = LoggerFactory.getLogger(TopKStrategyService.class);

    @Value("${search.topk.min:3}")
    private int minTopK;

    @Value("${search.topk.max:15}")
    private int maxTopK;

    @Value("${search.topk.default:5}")
    private int defaultTopK;

    // 问候/闲聊模式
    private static final List<Pattern> GREETING_PATTERNS = List.of(
            Pattern.compile("^(你好|您好|hi|hello|hey|嗨|哈喽|早上好|下午好|晚上好|晚安)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(谢谢|感谢|thanks|thank you|多谢|辛苦了)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(再见|拜拜|bye|goodbye|下次见|回见)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(你是谁|你叫什么|介绍一下自己)$", Pattern.CASE_INSENSITIVE)
    );

    // 简单概念查询模式
    private static final List<Pattern> SIMPLE_QUERY_PATTERNS = List.of(
            Pattern.compile("^(什么是|是什么|啥是|何为).{2,20}$"),
            Pattern.compile("^.{2,15}(是什么|是啥|啥意思|什么意思)$"),
            Pattern.compile("^怎么(读|念|写)$")
    );

    // 复杂查询模式（需要更多上下文）
    private static final List<Pattern> COMPLEX_QUERY_PATTERNS = List.of(
            Pattern.compile("(对比|比较|区别|差异|不同|相同|相似)"),
            Pattern.compile("(优缺点|利弊|好坏|优劣)"),
            Pattern.compile("(列举|列出|总结|归纳|概括|梳理)"),
            Pattern.compile("(流程|步骤|过程|方法|方式|策略|方案)"),
            Pattern.compile("(为什么|原因|分析|分析.*原因|.*的原因)"),
            Pattern.compile("(全面|详细|深入|系统|完整)")
    );

    /**
     * 根据查询内容计算合适的 topK 值
     *
     * @param query 用户查询
     * @return 推荐的 topK 值
     */
    public int calculateTopK(String query) {
        if (query == null || query.trim().isEmpty()) {
            return defaultTopK;
        }

        String trimmedQuery = query.trim();

        // 1. 检查是否为闲聊/问候（不需要检索）
        if (isGreeting(trimmedQuery)) {
            logger.debug("检测到闲聊查询，topK=0: {}", query);
            return 0;
        }

        // 2. 检查是否为简单概念查询（少量检索即可）
        if (isSimpleQuery(trimmedQuery)) {
            int topK = Math.max(minTopK, 3);
            logger.debug("检测到简单查询，topK={}: {}", topK, query);
            return topK;
        }

        // 3. 检查是否为复杂查询（需要更多上下文）
        if (isComplexQuery(trimmedQuery)) {
            int topK = Math.min(maxTopK, 10);
            logger.debug("检测到复杂查询，topK={}: {}", topK, query);
            return topK;
        }

        // 4. 根据查询长度调整
        int lengthBasedTopK = calculateByLength(trimmedQuery);

        // 5. 根据关键词数量调整
        int keywordCount = countKeywords(trimmedQuery);
        int keywordBasedTopK = Math.min(maxTopK, minTopK + keywordCount);

        // 取较大值，但不超过 maxTopK
        int finalTopK = Math.min(maxTopK, Math.max(lengthBasedTopK, keywordBasedTopK));

        logger.debug("计算 topK={}: query='{}', 长度={}, 关键词数={}", finalTopK, query, trimmedQuery.length(), keywordCount);

        return finalTopK;
    }

    /**
     * 判断是否为问候/闲聊
     */
    private boolean isGreeting(String query) {
        for (Pattern pattern : GREETING_PATTERNS) {
            if (pattern.matcher(query).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为简单概念查询
     */
    private boolean isSimpleQuery(String query) {
        for (Pattern pattern : SIMPLE_QUERY_PATTERNS) {
            if (pattern.matcher(query).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为复杂查询
     */
    private boolean isComplexQuery(String query) {
        int matchCount = 0;
        for (Pattern pattern : COMPLEX_QUERY_PATTERNS) {
            if (pattern.matcher(query).find()) {
                matchCount++;
            }
        }
        // 匹配 2 个以上复杂模式认为是复杂查询
        return matchCount >= 2;
    }

    /**
     * 根据查询长度计算 topK
     */
    private int calculateByLength(String query) {
        int length = query.length();

        if (length < 5) {
            return minTopK;
        } else if (length < 15) {
            return minTopK + 1;
        } else if (length < 30) {
            return defaultTopK;
        } else if (length < 50) {
            return defaultTopK + 2;
        } else {
            return Math.min(maxTopK, defaultTopK + 4);
        }
    }

    /**
     * 估算查询中的关键词数量
     */
    private int countKeywords(String query) {
        // 简单实现：按空格和常见分隔符分割
        String[] parts = query.split("[\\s,，。！？；;：:、]+");
        int count = 0;
        for (String part : parts) {
            if (part.length() >= 2) {
                count++;
            }
        }
        return Math.max(1, count);
    }
}
