package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.client.DeepSeekClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 意图识别服务
 * 识别用户输入的意图类型，用于路由到不同的处理流程
 * 
 * 意图类型：
 * - CHITCHAT: 闲聊（问候、感谢、告别等）
 * - KNOWLEDGE_QA: 知识问答（需要检索知识库）
 * - OPERATION: 操作指令（文件管理、系统操作等）
 * - UNKNOWN: 未知意图（默认走知识问答）
 */
@Service
public class IntentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognitionService.class);

    private final DeepSeekClient deepSeekClient;

    @Value("${intent.recognition.enabled:true}")
    private boolean enabled;

    @Value("${intent.recognition.use-llm:true}")
    private boolean useLlm;

    // 闲聊模式
    private static final List<Pattern> CHITCHAT_PATTERNS = List.of(
            // 问候
            Pattern.compile("^(你好|您好|hi|hello|hey|嗨|哈喽|早上好|下午好|晚上好|晚安|在吗|在不在)$", Pattern.CASE_INSENSITIVE),
            // 感谢
            Pattern.compile("^(谢谢|感谢|thanks|thank you|多谢|辛苦了|3q|thx)$", Pattern.CASE_INSENSITIVE),
            // 告别
            Pattern.compile("^(再见|拜拜|bye|goodbye|下次见|回见|886|88)$", Pattern.CASE_INSENSITIVE),
            // 自我介绍
            Pattern.compile("^(你是谁|你叫什么|介绍一下自己|你是什么|你是AI吗|你是机器人吗)$", Pattern.CASE_INSENSITIVE),
            // 能力询问
            Pattern.compile("^(你能做什么|你会什么|你有什么功能|帮我什么)$", Pattern.CASE_INSENSITIVE),
            // 简单回应
            Pattern.compile("^(好的|嗯|哦|知道了|明白了|了解|OK|ok|是的|对|没错)$", Pattern.CASE_INSENSITIVE)
    );

    // 操作指令模式
    private static final List<Pattern> OPERATION_PATTERNS = List.of(
            // 文件操作
            Pattern.compile("(上传|删除|查看|列表|搜索|查找).*(文件|文档)"),
            Pattern.compile("(我的|所有|全部).*(文件|文档)"),
            Pattern.compile("(文件|文档).*(列表|管理)"),
            // 系统操作
            Pattern.compile("(设置|配置|修改|更改).*(密码|个人信息)"),
            Pattern.compile("(退出|登出|注销)"),
            // 帮助
            Pattern.compile("^(帮助|help|怎么用|使用说明)$", Pattern.CASE_INSENSITIVE)
    );

    public IntentRecognitionService(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    /**
     * 识别用户输入的意图
     *
     * @param userInput 用户输入
     * @return 意图类型
     */
    public IntentType recognize(String userInput) {
        if (!enabled || userInput == null || userInput.trim().isEmpty()) {
            return IntentType.KNOWLEDGE_QA;
        }

        String input = userInput.trim();
        logger.debug("开始意图识别: '{}'", input);

        // 1. 规则匹配（快速判断）
        IntentType ruleIntent = recognizeByRules(input);
        if (ruleIntent != IntentType.UNKNOWN) {
            logger.info("规则匹配识别意图: '{}' -> {}", input, ruleIntent);
            return ruleIntent;
        }

        // 2. LLM 判断（更准确）
        if (useLlm) {
            IntentType llmIntent = recognizeByLlm(input);
            if (llmIntent != IntentType.UNKNOWN) {
                logger.info("LLM 识别意图: '{}' -> {}", input, llmIntent);
                return llmIntent;
            }
        }

        // 3. 默认返回知识问答
        logger.debug("默认意图: '{}' -> KNOWLEDGE_QA", input);
        return IntentType.KNOWLEDGE_QA;
    }

    /**
     * 使用规则匹配识别意图
     */
    private IntentType recognizeByRules(String input) {
        // 检查闲聊
        for (Pattern pattern : CHITCHAT_PATTERNS) {
            if (pattern.matcher(input).matches()) {
                return IntentType.CHITCHAT;
            }
        }

        // 检查操作指令
        for (Pattern pattern : OPERATION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return IntentType.OPERATION;
            }
        }

        return IntentType.UNKNOWN;
    }

    /**
     * 使用 LLM 识别意图
     */
    private IntentType recognizeByLlm(String input) {
        try {
            String systemPrompt = """
                你是一个意图识别助手。请判断用户输入的意图类型，只返回以下三种之一：
                - CHITCHAT: 闲聊（问候、感谢、告别、自我介绍等日常对话）
                - KNOWLEDGE_QA: 知识问答（询问知识、概念、方法、原因等需要检索知识库的问题）
                - OPERATION: 操作指令（文件管理、系统设置、帮助请求等需要执行操作）
                
                只返回意图类型，不要返回其他内容。
                """;

            String response = deepSeekClient.syncCall(systemPrompt, input);
            
            if (response != null) {
                String intent = response.trim().toUpperCase();
                if (intent.contains("CHITCHAT")) {
                    return IntentType.CHITCHAT;
                } else if (intent.contains("OPERATION")) {
                    return IntentType.OPERATION;
                } else if (intent.contains("KNOWLEDGE_QA")) {
                    return IntentType.KNOWLEDGE_QA;
                }
            }
        } catch (Exception e) {
            logger.warn("LLM 意图识别失败: {}", e.getMessage());
        }

        return IntentType.UNKNOWN;
    }

    /**
     * 意图类型枚举
     */
    public enum IntentType {
        CHITCHAT,        // 闲聊
        KNOWLEDGE_QA,    // 知识问答
        OPERATION,       // 操作指令
        UNKNOWN          // 未知
    }
}
