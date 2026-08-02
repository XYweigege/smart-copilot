package com.yizhaoqi.smartpai.utils;

import com.yizhaoqi.smartpai.config.SensitiveWordConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 敏感词过滤器（基于 DFA 算法）
 *
 * 功能：
 * 1. 支持内置默认词库 + application.yml 自定义词库
 * 2. 命中检测（contains）、命中词提取（findWords）
 * 3. 支持忽略大小写、忽略常见绕过字符（空格、*、全角等）
 */
@Component
public class SensitiveWordFilter {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordFilter.class);

    @Autowired
    private SensitiveWordConfig config;

    /** DFA 状态机根节点 */
    private final Map<Character, Object> root = new HashMap<>();

    /** 默认内置敏感词（兜底，防止配置遗漏导致完全无防护） */
    private static final List<String> DEFAULT_WORDS = Arrays.asList(
            // ===== 政治类（示例，请按合规要求补充） =====
            "法轮功", "台独", "港独", "疆独", "藏独", "反共",
            // ===== 暴力/违法类 =====
            "制毒", "贩毒", "制造毒品", "买卖枪支", "爆炸物制作",
            "黑客攻击教程", "入侵网站教程", "制造炸弹",
            // ===== 色情低俗类 =====
            "裸聊", "约炮", "成人网站", "色情视频", "黄片下载",
            // ===== 诈骗/赌博类 =====
            "赌博平台", "博彩网站", "网络赌博", "刷单返利", "杀猪盘",
            // ===== 辱骂/歧视类 =====
            "傻逼", "草泥马", "贱人", "废物东西"
    );

    @PostConstruct
    public void init() {
        root.clear();
        List<String> words = new ArrayList<>();
        if (config.isUseDefaultWords()) {
            words.addAll(DEFAULT_WORDS);
        }
        if (config.getWords() != null) {
            words.addAll(config.getWords());
        }
        // 去空、去重、去首尾空格
        Set<String> unique = new LinkedHashSet<>();
        for (String w : words) {
            if (w != null && !w.trim().isEmpty()) {
                unique.add(w.trim());
            }
        }
        for (String word : unique) {
            addWord(word);
        }
        logger.info("敏感词过滤器初始化完成，共加载 {} 个敏感词，启用状态={}", unique.size(), config.isEnabled());
    }

    /** 将单个敏感词加入 DFA */
    private void addWord(String word) {
        Map<Character, Object> node = root;
        for (char c : word.toCharArray()) {
            node = (Map<Character, Object>) node.computeIfAbsent(c, k -> new HashMap<Character, Object>());
        }
        node.put('\0', '\0'); // 结束标记
    }

    /**
     * 预处理文本：转小写、去除常见绕过字符
     */
    private String normalize(String text) {
        if (text == null) return "";
        // 转小写，便于忽略大小写匹配
        String t = text.toLowerCase(Locale.ROOT);
        // 去除空格、*、全角空格、零宽字符等绕过手段
        t = t.replaceAll("[\\s\\*　­​­­]", "");
        return t;
    }

    /**
     * 是否包含敏感词
     */
    public boolean containsSensitiveWord(String text) {
        if (!config.isEnabled() || text == null || text.isEmpty()) {
            return false;
        }
        String normalized = normalize(text);
        int len = normalized.length();
        for (int i = 0; i < len; i++) {
            if (dfsMatch(normalized, i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出文本中命中的全部敏感词（去重）
     */
    public List<String> findSensitiveWords(String text) {
        List<String> result = new ArrayList<>();
        if (!config.isEnabled() || text == null || text.isEmpty()) {
            return result;
        }
        String normalized = normalize(text);
        int len = normalized.length();
        Set<String> found = new LinkedHashSet<>();
        for (int i = 0; i < len; i++) {
            String hit = dfsMatchWithWord(normalized, i);
            if (hit != null) {
                found.add(hit);
            }
        }
        result.addAll(found);
        return result;
    }

    /** 从位置 start 开始做 DFA 匹配，返回是否匹配到 */
    private boolean dfsMatch(String text, int start) {
        Map<Character, Object> node = root;
        for (int i = start; i < text.length(); i++) {
            Object next = node.get(text.charAt(i));
            if (next == null) {
                return false;
            }
            node = (Map<Character, Object>) next;
            if (node.containsKey('\0')) {
                return true;
            }
        }
        return false;
    }

    /** 从位置 start 开始做 DFA 匹配，返回命中的敏感词原文（不带绕过字符） */
    private String dfsMatchWithWord(String text, int start) {
        Map<Character, Object> node = root;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < text.length(); i++) {
            Object next = node.get(text.charAt(i));
            if (next == null) {
                return null;
            }
            node = (Map<Character, Object>) next;
            sb.append(text.charAt(i));
            if (node.containsKey('\0')) {
                return sb.toString();
            }
        }
        return null;
    }
}
