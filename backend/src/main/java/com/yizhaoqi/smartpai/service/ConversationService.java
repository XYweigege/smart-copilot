package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.Conversation;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.ConversationRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 记录用户的对话历史。
     *
     * @param username 用户名
     * @param question 用户提问内容
     * @param answer 系统回答内容
     */
    public void recordConversation(String username, String question, String answer) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setQuestion(question);
        conversation.setAnswer(answer);

        conversationRepository.save(conversation);
    }

    /**
     * 记录用户的对话历史（按 username 落库，供 ChatHandler 调用，带 conversationId）。
     *
     * @param username 用户名
     * @param conversationId 会话 UUID（与 Redis 中的会话 ID 一致）
     * @param question 用户提问内容
     * @param answer 系统回答内容
     */
    public void recordConversationWithConversationId(String username, String conversationId, String question, String answer) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setConversationId(conversationId);
        conversation.setQuestion(question);
        conversation.setAnswer(answer);

        conversationRepository.save(conversation);
    }

    /**
     * 从 MySQL 按会话 ID 聚合出完整的消息列表，作为 Redis 过期后的兜底数据源。
     *
     * MySQL 中每条记录是一问一答（question/answer 各一行语义为同一轮），
     * 这里将其还原成与 Redis 中 {@code conversation:{id}} 一致的扁平消息流：
     * 每一轮拆成 user 消息 + assistant 消息，并按时间戳升序排列。
     *
     * @param conversationId 会话 ID
     * @return 扁平化消息列表，元素格式为 {role, content, timestamp}
     */
    public List<Map<String, String>> getHistoryFromMysql(String conversationId) {
        List<Conversation> rows = conversationRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        List<Map<String, String>> messages = new ArrayList<>();
        for (Conversation row : rows) {
            String ts = row.getTimestamp() != null ? row.getTimestamp().toString() : "";
            Map<String, String> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", row.getQuestion() != null ? row.getQuestion() : "");
            userMsg.put("timestamp", ts);
            messages.add(userMsg);

            Map<String, String> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", row.getAnswer() != null ? row.getAnswer() : "");
            assistantMsg.put("timestamp", ts);
            messages.add(assistantMsg);
        }
        return messages;
    }

    /**
     * 查询用户的对话历史。
     *
     * @param username 用户名
     * @param startDate 起始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 符合条件的对话记录列表
     */
    public List<Conversation> getConversations(String username, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        // 检查用户角色，如果是管理员且username参数为"all"，则返回所有对话历史
        if (user.getRole() == User.Role.ADMIN && "all".equals(username)) {
            if (startDate != null && endDate != null) {
                return conversationRepository.findByTimestampBetween(startDate, endDate);
            } else {
                return conversationRepository.findAll();
            }
        } else {
            // 普通用户只能查看自己的对话历史
            if (startDate != null && endDate != null) {
                return conversationRepository.findByUserIdAndTimestampBetween(
                        user.getId(), startDate, endDate);
            } else {
                return conversationRepository.findByUserId(user.getId());
            }
        }
    }
    
    /**
     * 管理员查询所有用户的对话历史。
     *
     * @param adminUsername 管理员用户名
     * @param targetUsername 目标用户名（可选，如果提供则只查询该用户的对话历史）
     * @param startDate 起始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 符合条件的对话记录列表
     */
    public List<Conversation> getAllConversations(String adminUsername, String targetUsername, 
                                                 LocalDateTime startDate, LocalDateTime endDate) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new CustomException("Admin not found", HttpStatus.NOT_FOUND));
        
        // 验证用户是否为管理员
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Unauthorized access", HttpStatus.FORBIDDEN);
        }
        
        // 如果指定了目标用户，则只查询该用户的对话历史
        if (targetUsername != null && !targetUsername.isEmpty()) {
            User targetUser = userRepository.findByUsername(targetUsername)
                    .orElseThrow(() -> new CustomException("Target user not found", HttpStatus.NOT_FOUND));
            
            if (startDate != null && endDate != null) {
                return conversationRepository.findByUserIdAndTimestampBetween(
                        targetUser.getId(), startDate, endDate);
            } else {
                return conversationRepository.findByUserId(targetUser.getId());
            }
        } else {
            // 否则查询所有用户的对话历史
            if (startDate != null && endDate != null) {
                return conversationRepository.findByTimestampBetween(startDate, endDate);
            } else {
                return conversationRepository.findAll();
            }
        }
    }
}