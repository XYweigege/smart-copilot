package com.yizhaoqi.smartpai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 敏感词过滤器（DFA 算法）
 *
 * 功能：
 * 1. 基于 DFA（确定性有限自动机）构建敏感词树，匹配效率高，O(n) 扫描一次即可。
 * 2. 支持大小写不敏感匹配（统一转小写）。
 * 3. 支持从配置注入自定义词库，并默认加载内置兜底词库。
 * 4. 提供 contains(String) 与 findAll(String) 两种查询方式。
 *
 * 注意：本类仅做"文本匹配"，是否拦截由调用方（HTTP 过滤器 / WebSocket）决定，
 * 以此保持职责单一，且便于单元测试。
 */
@Component
public class SensitiveWordFilter implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordFilter.class);

    /** DFA 状态节点：字符 -> 子节点 */
    private final Map<Character, Node> root = new HashMap<>();

    /** 内置兜底敏感词（占位示例，部署时请按合规要求替换为实际词库） */
    private static final List<String> DEFAULT_WORDS = Arrays.asList(
            // 政治与领导人（政治敏感）
            "政治敏感词1", "政治敏感词2",
            // 违禁与违法
            "制作炸弹", "制造毒品", "非法集会", "颠覆国家",
            // 辱骂与歧视
            "傻逼", "滚蛋", "去死", "垃圾人",
            // 示例占位（便于演示拦截效果，可自行保留或删除）
            "违规测试词"
    );

    private final SensitiveWordConfig config;

    /** 当前生效的敏感词列表（供审计/日志展示数量） */
    private volatile List<String> activeWords = new ArrayList<>();

    public SensitiveWordFilter(SensitiveWordConfig config) {
        this.config = config;
    }

    @Override
    public void afterPropertiesSet() {
        rebuild();
    }

    /**
     * 根据配置重建 DFA 词库（支持运行时刷新）
     */
    public synchronized void rebuild() {
        root.clear();
        List<String> merged = new ArrayList<>();
        if (config.isUseDefaultWords()) {
            merged.addAll(DEFAULT_WORDS);
        }
        if (config.getWords() != null) {
            merged.addAll(config.getWords());
        }
        for (String word : merged) {
            if (word != null && !word.isBlank()) {
                addWord(word.trim());
            }
        }
        activeWords = new ArrayList<>(merged);
        logger.info("敏感词过滤器已加载，生效词数: {}", activeWords.size());
    }

    private void addWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        Map<Character, Node> current = root;
        Node last = null;
        for (char c : lower.toCharArray()) {
            Node node = current.get(c);
            if (node == null) {
                node = new Node();
                current.put(c, node);
            }
            last = node;
            current = node.children;
        }
        if (last != null) {
            last.end = true;
        }
    }

    /**
     * 判断文本是否包含敏感词
     */
    public boolean contains(String text) {
        if (!config.isEnabled() || text == null || text.isEmpty()) {
            return false;
        }
        return findFirst(text) != null;
    }

    /**
     * 返回文本中命中的所有敏感词（去重）
     */
    public Set<String> findAll(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (!config.isEnabled() || text == null || text.isEmpty()) {
            return result;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int n = lower.length();
        for (int i = 0; i < n; i++) {
            Map<Character, Node> current = root;
            int j = i;
            StringBuilder sb = new StringBuilder();
            while (j < n) {
                Node node = current.get(lower.charAt(j));
                if (node == null) {
                    break;
                }
                sb.append(lower.charAt(j));
                if (node.end) {
                    result.add(sb.toString());
                }
                current = node.children;
                j++;
            }
        }
        return result;
    }

    /**
     * 返回第一个命中的敏感词（用于快速判断），无则返回 null
     */
    private String findFirst(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int n = lower.length();
        for (int i = 0; i < n; i++) {
            Map<Character, Node> current = root;
            int j = i;
            StringBuilder sb = new StringBuilder();
            while (j < n) {
                Node node = current.get(lower.charAt(j));
                if (node == null) {
                    break;
                }
                sb.append(lower.charAt(j));
                if (node.end) {
                    return sb.toString();
                }
                current = node.children;
                j++;
            }
        }
        return null;
    }

    public List<String> getActiveWords() {
        return Collections.unmodifiableList(activeWords);
    }

    /** DFA 节点 */
    private static class Node {
        boolean end = false;
        Map<Character, Node> children = new HashMap<>();

        Node() {
            // 标记是否为词尾
        }
    }
}
