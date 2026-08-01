package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.handler.ChatWebSocketHandler;
import com.yizhaoqi.smartpai.service.ChatHandler;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;

@Component
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController extends TextWebSocketHandler {

    private final ChatHandler chatHandler;

    public ChatController(ChatHandler chatHandler) {
        this.chatHandler = chatHandler;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userMessage = message.getPayload();
        String userId = session.getId(); // Use session ID as userId for simplicity
        
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("WEBSOCKET_CHAT");
        try {
            LogUtils.logChat(userId, session.getId(), "USER_MESSAGE", userMessage.length());
            LogUtils.logBusiness("WEBSOCKET_CHAT", userId, "处理WebSocket聊天消息: messageLength=%d", userMessage.length());
            
        chatHandler.processMessage(userId, userMessage, session);
            
            LogUtils.logUserOperation(userId, "WEBSOCKET_CHAT", "message_processing", "SUCCESS");
            monitor.end("WebSocket消息处理成功");
        } catch (Exception e) {
            LogUtils.logBusinessError("WEBSOCKET_CHAT", userId, "WebSocket消息处理失败", e);
            monitor.end("WebSocket消息处理失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 获取WebSocket停止指令Token
     */
    @GetMapping("/websocket-token")
    public ResponseEntity<?> getWebSocketToken() {
        try {
            String cmdToken = ChatWebSocketHandler.getInternalCmdToken();
            
            // 检查token是否有效
            if (cmdToken == null || cmdToken.trim().isEmpty()) {
                return ResponseEntity.status(500).body(Map.of(
                    "code", 500,
                    "message", "Token生成失败",
                    "data", null
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取WebSocket停止指令Token成功",
                "data", Map.of("cmdToken", cmdToken)
            ));
            
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_WEBSOCKET_TOKEN", "system", "获取WebSocket Token失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "服务器内部错误：" + e.getMessage(),
                "data", null
            ));
        }
    }

    /**
     * 新建会话 - 清除当前会话，创建新会话ID
     * 用于减少 token 消耗，避免历史对话过长
     */
    @PostMapping("/new-conversation")
    public ResponseEntity<?> newConversation() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            
            String newConversationId = chatHandler.createNewConversation(userId);
            
            LogUtils.logUserOperation(userId, "NEW_CONVERSATION", "create_new_conversation", "SUCCESS");
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "新会话创建成功",
                "data", Map.of("conversationId", newConversationId)
            ));
            
        } catch (Exception e) {
            LogUtils.logBusinessError("NEW_CONVERSATION", "system", "新建会话失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "新建会话失败：" + e.getMessage(),
                "data", null
            ));
        }
    }

    /**
     * 获取历史会话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            
            List<Map<String, Object>> conversations = chatHandler.getConversationHistoryList(userId);
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "获取历史会话列表成功",
                "data", conversations
            ));
            
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_CONVERSATIONS", "system", "获取历史会话列表失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "获取历史会话列表失败：" + e.getMessage(),
                "data", null
            ));
        }
    }

    /**
     * 切换到指定的历史会话
     */
    @PostMapping("/switch-conversation")
    public ResponseEntity<?> switchConversation(@org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            String conversationId = body.get("conversationId");
            
            if (conversationId == null || conversationId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "conversationId 不能为空",
                    "data", null
                ));
            }
            
            List<Map<String, String>> history = chatHandler.switchToConversation(userId, conversationId);
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "切换会话成功",
                "data", Map.of(
                    "conversationId", conversationId,
                    "messages", history
                )
            ));
            
        } catch (Exception e) {
            LogUtils.logBusinessError("SWITCH_CONVERSATION", "system", "切换会话失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "切换会话失败：" + e.getMessage(),
                "data", null
            ));
        }
    }

    /**
     * 删除指定的历史会话
     */
    @PostMapping("/delete-conversation")
    public ResponseEntity<?> deleteConversation(@org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            String conversationId = body.get("conversationId");
            
            if (conversationId == null || conversationId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "conversationId 不能为空",
                    "data", null
                ));
            }
            
            chatHandler.deleteConversation(userId, conversationId);
            
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "删除会话成功",
                "data", null
            ));
            
        } catch (Exception e) {
            LogUtils.logBusinessError("DELETE_CONVERSATION", "system", "删除会话失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "code", 500,
                "message", "删除会话失败：" + e.getMessage(),
                "data", null
            ));
        }
    }
}
