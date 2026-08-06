package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.service.ChatHandler;
import com.yizhaoqi.smartpai.service.ChatOutput;
import com.yizhaoqi.smartpai.service.SseChatOutput;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatHandler chatHandler;
    private final JwtUtils jwtUtils;

    // 会话Id -> SSE 输出通道，便于"继续聊天"与状态查询
    private final ConcurrentHashMap<String, SseChatOutput> sseSessions = new ConcurrentHashMap<>();

    public ChatController(ChatHandler chatHandler, JwtUtils jwtUtils) {
        this.chatHandler = chatHandler;
        this.jwtUtils = jwtUtils;
    }

    /**
     * SSE 聊天流式端点。
     * 前端通过 EventSource 连接；鉴权走 Authorization header（Bearer token）。
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChat(@RequestParam("message") String message,
                                 @RequestParam(value = "conversationId", required = false) String conversationId,
                                 @RequestParam(value = "token", required = false) String token,
                                 HttpServletRequest request) {

        // EventSource 不支持自定义 header，鉴权走 token 查询参数
        String userId = resolveUserId(token, request);
        String clientIp = request.getRemoteAddr();
        String sessionId = java.util.UUID.randomUUID().toString();

        SseEmitter emitter = new SseEmitter(0L); // 0 = 不超时，由 nginx/客户端管理
        SseChatOutput output = new SseChatOutput(emitter, sessionId, clientIp);
        sseSessions.put(sessionId, output);

        emitter.onCompletion(() -> sseSessions.remove(sessionId));
        emitter.onTimeout(() -> {
            emitter.complete();
            sseSessions.remove(sessionId);
        });
        emitter.onError((e) -> sseSessions.remove(sessionId));

        // 先下发 meta 事件，告知前端本次会话标识
        try {
            emitter.send(SseEmitter.event()
                    .name("meta")
                    .data("{\"type\":\"connection\",\"sessionId\":\"" + sessionId + "\"}")
                    .id(sessionId));
        } catch (Exception e) {
            logger.warn("下发 SSE meta 事件失败: {}", e.getMessage());
        }

        logger.info("SSE 聊天连接建立，用户ID: {}，会话ID: {}，IP: {}", userId, sessionId, clientIp);
        chatHandler.processMessage(userId, message, output);

        return emitter;
    }

    /**
     * 停止当前用户的响应生成。
     */
    @PostMapping("/stop")
    public ResponseEntity<?> stopChat(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestBody(required = false) Map<String, String> body,
                                      HttpServletRequest request) {
        String userId = resolveUserId(authorization, request);
        String sessionId = body != null ? body.get("sessionId") : null;
        logger.info("收到停止请求，用户ID: {}，会话ID: {}", userId, sessionId);

        if (sessionId != null && !sessionId.isEmpty()) {
            SseChatOutput output = sseSessions.get(sessionId);
            if (output != null) {
                output.markStopped();
                output.complete(); // 关闭 SSE 通道，前端 onComplete 触发
            }
        }
        chatHandler.stopResponse(userId, sessionId);

        return ResponseEntity.ok(Map.of("code", 200, "message", "ok",
                "data", Map.of("status", "stopped", "sessionId", sessionId != null ? sessionId : "")));
    }

    /**
     * 新建会话：清除当前会话上下文，返回新的 conversationId。
     */
    @PostMapping("/new-conversation")
    public ResponseEntity<?> newConversation(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest request) {
        String userId = resolveUserId(authorization, request);
        String conversationId = chatHandler.createNewConversation(userId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", Map.of("conversationId", conversationId)));
    }

    /**
     * 获取当前用户的历史会话列表。
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> conversationList(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              HttpServletRequest request) {
        String userId = resolveUserId(authorization, request);
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", chatHandler.getConversationHistoryList(userId)));
    }

    /**
     * 切换到指定历史会话并返回其消息。
     */
    @PostMapping("/switch-conversation")
    public ResponseEntity<?> switchConversation(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestBody Map<String, String> body,
                                                HttpServletRequest request) {
        String userId = resolveUserId(authorization, request);
        String conversationId = body != null ? body.get("conversationId") : null;
        List<Map<String, String>> messages = chatHandler.switchToConversation(userId, conversationId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok",
                "data", Map.of("conversationId", conversationId, "messages", messages)));
    }

    /**
     * 删除指定历史会话。
     */
    @PostMapping("/delete-conversation")
    public ResponseEntity<?> deleteConversation(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestBody Map<String, String> body,
                                                HttpServletRequest request) {
        String userId = resolveUserId(authorization, request);
        String conversationId = body != null ? body.get("conversationId") : null;
        chatHandler.deleteConversation(userId, conversationId);
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok",
                "data", Map.of("status", "deleted", "conversationId", conversationId)));
    }

    /**
     * 根据会话内的引用编号查询对应的文件 MD5（用于文档溯源）。
     */
    @GetMapping("/reference")
    public ResponseEntity<?> reference(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestParam("sessionId") String sessionId,
                                       @RequestParam("referenceNumber") int referenceNumber,
                                       HttpServletRequest request) {
        resolveUserId(authorization, request);
        String fileMd5 = chatHandler.getReferenceMd5(sessionId, referenceNumber);
        if (fileMd5 == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", 404, "message", "未找到引用映射"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", Map.of("fileMd5", fileMd5)));
    }

    private String resolveUserId(String tokenParam, HttpServletRequest request) {
        if (tokenParam != null && !tokenParam.isEmpty()) {
            String token = tokenParam;
            if (token.toLowerCase().startsWith("bearer ")) {
                token = token.substring(7).trim();
            }
            String username = jwtUtils.extractUsernameFromToken(token);
            if (username != null) {
                return username;
            }
        }
        // 回退：尝试从 SecurityContext 取
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        // 最后一重回退（不应发生，前端必带 token）
        String ip = request.getRemoteAddr();
        logger.warn("无法解析用户身份，使用 IP 作为匿名标识: {}", ip);
        return "anon_" + ip;
    }
}
